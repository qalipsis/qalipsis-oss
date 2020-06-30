package io.evolue.core.head.campaign

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.lang.concurrentSet
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveKey
import io.evolue.core.cross.driving.directives.DirectiveProducer
import io.evolue.core.cross.driving.directives.MinionsCreationDirectiveReference
import io.evolue.core.cross.driving.directives.MinionsCreationPreparationDirective
import io.evolue.core.cross.driving.directives.MinionsRampUpPreparationDirective
import io.evolue.core.cross.driving.feedback.DirectiveFeedback
import io.evolue.core.cross.driving.feedback.Feedback
import io.evolue.core.cross.driving.feedback.FeedbackConsumer
import io.evolue.core.cross.driving.feedback.FeedbackStatus
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import io.micronaut.context.annotation.Value
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Singleton
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero
import kotlin.reflect.KClass

/**
 * Component to manage a new Campaign.
 *
 * The component inherits from [DirectiveProcessor] because when a directive is delegated to factories,the head has to be aware of them.
 *
 * @author Eric Jessé
 *
 * @property minionsCountPerScenario when set to a non-null value, specifies the number of minions to create for each scenario.
 * @property minionsCountFactor when minionsCountPerCampaign is not set, the factor applies to the default minions count of each scenario.
 * @property speedFactor speed factor for the ramp-up.
 * @property startOffsetMs offset (in milliseconds) to apply to the ramp-up directive to be sure all the directives for all the scenarios are received when it really comes to start.
 * @property feedbackConsumer consumer for feedback from directives, coming from the factories.
 * @property scenarioRepository providers for scenario metadata.
 * @property directiveProducer producer to send directives to the factories.
 */
@Singleton
internal class DefaultCampaignManager(
        @PositiveOrZero @Value("\${campaign.minions-count-per-scenario}") private val minionsCountPerScenario: Int = 0,
        @Positive @Value("\${campaign.minions-factor}") private val minionsCountFactor: Double = 1.0,
        @Positive @Value("\${campaign.speed-factor}") private val speedFactor: Double = 1.0,
        @Positive @Value("\${campaign.start-offset-ms}") private val startOffsetMs: Long = 1000,
        private val feedbackConsumer: FeedbackConsumer,
        private val scenarioRepository: HeadScenarioRepository,
        private val directiveProducer: DirectiveProducer
) : CampaignManager, DirectiveProcessor<Directive> {

    /**
     * Scenarios to include in the campaign.
     */
    private val scenarios = ConcurrentHashMap<ScenarioId, HeadScenario>()

    /**
     * Directives related to the campaign that are being processed..
     */
    private val directivesInProgress = ConcurrentHashMap<DirectiveKey, DirectiveInProgress<*>>()

    /**
     * Dags for which the minions were all successfully created.
     */
    private val readyDagsByScenario = ConcurrentHashMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>()

    /**
     * Scenarios that are ready to start.
     */
    private val readyScenario = concurrentSet<ScenarioId>()

    /**
     * Action to execute when a critical problem occurs during the campaign.
     */
    private var onCriticalFailure: (String) -> Unit = { _ -> }

    private var consumptionJob: Job? = null

    @PostConstruct
    fun init() {
        consumptionJob = GlobalScope.launch {
            log.debug("Consuming from $feedbackConsumer")
            feedbackConsumer.onReceive { feedback ->
                processFeedBack(feedback)
            }
        }
    }

    @PreDestroy
    fun destroy() {
        consumptionJob?.cancel()
    }

    /**
     * Start a new campaign for the provided scenarios.
     */
    @LogInput
    override suspend fun start(id: CampaignId, scenarios: List<ScenarioId>, onCriticalFailure: (String) -> Unit) {
        this.directivesInProgress.clear()
        this.scenarios.clear()
        this.readyDagsByScenario.clear()
        this.readyScenario.clear()
        this.onCriticalFailure = onCriticalFailure

        scenarioRepository.getAll(scenarios).forEach { scenario ->
            this.scenarios[scenario.id] = scenario
            triggerMinionsCreation(id, scenario)
        }
    }

    /**
     * Create the directive to create all the minions for the given scenario.
     */
    private suspend fun triggerMinionsCreation(id: CampaignId, scenario: HeadScenario) {
        log.debug(
            "Campaign ${id}, scenario ${scenario.id} - creating the directive to create the IDs for all the minions")
        val count =
            if (minionsCountPerScenario > 0) minionsCountPerScenario else (scenario.minionsCount * minionsCountFactor).toInt()

        val directive = MinionsCreationPreparationDirective(id, scenario.id, count)
        directivesInProgress[directive.key] = DirectiveInProgress(directive)
        directiveProducer.publish(directive)
    }

    /**
     * Only accept directives related to the campaigns, that are not generated by the current implementation.
     */
    @LogInputAndOutput
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsCreationDirectiveReference
    }

    /**
     * Simply keep the directive into the cache.
     */
    @LogInput
    override suspend fun process(directive: Directive) {
        directivesInProgress[directive.key] = DirectiveInProgress(directive)
    }

    override fun order(): Int {
        return Int.MIN_VALUE
    }

    private suspend fun processFeedBack(feedback: Feedback) {
        when (feedback) {
            is DirectiveFeedback -> {
                log.debug("Processing a directive feedback: ${feedback}")
                processDirectiveFeedback(feedback)
            }
        }
    }

    /**
     * Broadcast the received directive feedback to the relevant method.
     */
    @VisibleForTesting
    internal suspend fun processDirectiveFeedback(feedback: DirectiveFeedback) {
        if (directivesInProgress.containsKey(feedback.directiveKey)) {
            val directiveInProgress = directivesInProgress[feedback.directiveKey]!!
            when {
                directiveInProgress.isA(MinionsCreationPreparationDirective::class)
                -> receivedMinionsCreationPreparationFeedback(feedback, directiveInProgress.get())
                directiveInProgress.isA(MinionsCreationDirectiveReference::class)
                -> receiveMinionsCreationDirectiveFeedback(feedback, directiveInProgress.get())
            }
            // Remove the directive is done, whether failed or successful.
            if (feedback.status.isDone) {
                directivesInProgress.remove(feedback.directiveKey)
            }
        }
    }

    /**
     * Log the feedback from a [MinionsCreationPreparationDirective] or trigger the critical failure action when the feedback is in failure.
     */
    @VisibleForTesting
    internal fun receivedMinionsCreationPreparationFeedback(feedback: DirectiveFeedback,
            directive: MinionsCreationPreparationDirective) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug(
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId} - IDs for all the minions are being created")
            FeedbackStatus.FAILED -> onCriticalFailure(
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId} - IDs for all the minions could not be created: ${feedback.error}")
            FeedbackStatus.COMPLETED -> {
                log.debug("Scenario ${directive.scenarioId} - IDs for all the minions were created")
            }
        }
    }

    /**
     * Process the feedback from a [MinionsCreationDirectiveReference].
     */
    @VisibleForTesting
    internal suspend fun receiveMinionsCreationDirectiveFeedback(feedback: DirectiveFeedback,
            directive: MinionsCreationDirectiveReference) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug(
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions are being created")
            FeedbackStatus.FAILED -> onCriticalFailure(
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions could not be created: ${feedback.error}")
            FeedbackStatus.COMPLETED -> {
                log.debug(
                    "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions were created")

                with(readyDagsByScenario.computeIfAbsent(directive.scenarioId) { concurrentSet() }) {
                    add(directive.dagId)
                    // When all the DAGs of the scenario are ready, the scenario is marked ready.
                    if (scenarios.containsKey(directive.scenarioId)
                        && size == scenarios[directive.scenarioId]!!.directedAcyclicGraphs.size
                    ) {
                        onScenarioReady(directive.campaignId, directive.scenarioId)
                    }
                }
            }
        }
    }

    /**
     * Mark the scenario ready and triggers [onAllScenariosReady] if all the others are ready.
     */
    private suspend fun onScenarioReady(campaignId: CampaignId, scenarioId: ScenarioId) {
        log.debug("Campaign ${campaignId}, scenario ${scenarioId} - All minions were created")
        readyScenario.add(scenarioId)
        if (readyScenario.size == scenarios.keys.size) {
            onAllScenariosReady(campaignId)
        }
    }

    /**
     * Trigger the ramp-up when all the minions for all the scenarios are ready.
     */
    private suspend fun onAllScenariosReady(campaignId: CampaignId) {
        log.info("All minions for all the scenarios were created, now preparing the ramp-up")
        scenarios.keys.forEach { scenarioId ->
            val directive = MinionsRampUpPreparationDirective(campaignId, scenarioId, startOffsetMs, speedFactor)
            directivesInProgress[directive.key] = DirectiveInProgress(directive)
            directiveProducer.publish(directive)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

    data class DirectiveInProgress<T : Directive>(
            val directive: T
    ) {

        fun isA(directiveClass: KClass<out Directive>) = directiveClass.isInstance(directive)

        fun <U> get(): U {
            return directive as U
        }
    }
}
