package io.evolue.core.heads.campaigns

import io.evolue.api.annotations.VisibleForTest
import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.lang.concurrentSet
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.directives.Directive
import io.evolue.api.orchestration.directives.DirectiveKey
import io.evolue.api.orchestration.directives.DirectiveProcessor
import io.evolue.api.orchestration.directives.DirectiveProducer
import io.evolue.api.orchestration.feedbacks.DirectiveFeedback
import io.evolue.api.orchestration.feedbacks.Feedback
import io.evolue.api.orchestration.feedbacks.FeedbackConsumer
import io.evolue.api.orchestration.feedbacks.FeedbackStatus
import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.directives.CampaignStartDirective
import io.evolue.core.cross.directives.MinionsCreationDirectiveReference
import io.evolue.core.cross.directives.MinionsCreationPreparationDirective
import io.evolue.core.cross.directives.MinionsRampUpPreparationDirective
import io.evolue.core.cross.feedbacks.CampaignStartedForDagFeedback
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
 * Component to manage a new Campaign for all the known scenarios.
 *
 * The component inherits from [DirectiveProcessor] to be aware of the directive delegated to the factories, the head has to be aware of them.
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
     * Dags that are ready to be started.
     */
    private val readyDagsByScenario = ConcurrentHashMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>()

    /**
     * Dags for which the campaign was successfully started.
     */
    private val startedDagsByScenario = ConcurrentHashMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>()

    /**
     * Scenarios that are ready to start.
     */
    private val readyScenarios = concurrentSet<ScenarioId>()

    /**
     * Scenarios that are started.
     */
    private val startedScenarios = concurrentSet<ScenarioId>()

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
        directivesInProgress.clear()
        this.scenarios.clear()
        readyDagsByScenario.clear()
        readyScenarios.clear()
        startedDagsByScenario.clear()
        startedScenarios.clear()

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

        readyDagsByScenario[scenario.id] = concurrentSet()
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

    @VisibleForTest
    @LogInput
    internal suspend fun processFeedBack(feedback: Feedback) {
        when (feedback) {
            is DirectiveFeedback -> {
                log.debug("Processing a directive feedback: ${feedback}")
                processDirectiveFeedback(feedback)
            }
            is CampaignStartedForDagFeedback -> receiveCampaignStartedFeedback(feedback)
        }
    }

    /**
     * Broadcast the received directive feedback to the relevant method.
     */
    @VisibleForTest
    internal suspend fun processDirectiveFeedback(feedback: DirectiveFeedback) {
        if (directivesInProgress.containsKey(feedback.directiveKey)) {
            val directiveInProgress = directivesInProgress[feedback.directiveKey]!!
            when {
                directiveInProgress.isA(MinionsCreationPreparationDirective::class)
                -> receivedMinionsCreationPreparationFeedback(feedback, directiveInProgress.get())
                directiveInProgress.isA(MinionsCreationDirectiveReference::class)
                -> receiveMinionsCreationDirectiveFeedback(feedback, directiveInProgress.get())
            }
            // Remove the failed directive to avoid further processing.
            if (feedback.status == FeedbackStatus.FAILED) {
                directivesInProgress.remove(feedback.directiveKey)
            }
        }
    }

    /**
     * Log the feedback from a [MinionsCreationPreparationDirective] or trigger the critical failure action when the feedback is in failure.
     */
    @VisibleForTest
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
    @VisibleForTest
    internal suspend fun receiveMinionsCreationDirectiveFeedback(feedback: DirectiveFeedback,
                                                                 directive: MinionsCreationDirectiveReference) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug(
                    "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions are being created")
            FeedbackStatus.FAILED -> {
                // Prevents the completed feedbacks to be processed.
                readyDagsByScenario.clear()
                readyScenarios.clear()
                // TODO Cancel the campaign.
                onCriticalFailure(
                        "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions could not be created: ${feedback.error}")
            }
            FeedbackStatus.COMPLETED -> {
                readyDagsByScenario[directive.scenarioId]?.apply {
                    log.debug(
                            "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions were created")
                    add(directive.dagId)
                    // When all the DAGs of the scenario are ready, the scenario is marked ready.
                    if (scenarios.containsKey(directive.scenarioId)
                        && size == scenarios[directive.scenarioId]!!.directedAcyclicGraphs.size
                    ) {
                        readyDagsByScenario.remove(directive.scenarioId)
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
        readyScenarios.add(scenarioId)
        if (readyScenarios.size == scenarios.keys.size) {
            onAllScenariosReady(campaignId)
        }
    }

    /**
     * Trigger the ramp-up when all the minions for all the scenarios are ready.
     */
    private suspend fun onAllScenariosReady(campaignId: CampaignId) {
        log.info("All minions for all the scenarios were created, now starting the campaign")
        readyDagsByScenario.clear()
        directivesInProgress.clear()
        startedDagsByScenario.clear()
        startedScenarios.clear()

        readyScenarios.forEach { scenarioId ->
            startedDagsByScenario[scenarioId] = concurrentSet()
            val directive = CampaignStartDirective(campaignId, scenarioId)
            directivesInProgress[directive.key] = DirectiveInProgress(directive)
            directiveProducer.publish(directive)
        }
        readyScenarios.clear()
    }

    private suspend fun receiveCampaignStartedFeedback(feedback: CampaignStartedForDagFeedback) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug(
                    "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign is being started")
            FeedbackStatus.FAILED -> {
                // Prevents the completed feedbacks to be processed.
                startedDagsByScenario.clear()
                readyScenarios.clear()
                // TODO Cancel the campaign.
                onCriticalFailure(
                        "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign could not be started: ${feedback.error}")
            }
            FeedbackStatus.COMPLETED -> {
                startedDagsByScenario[feedback.scenarioId]?.apply {
                    log.debug(
                            "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign is started")
                    add(feedback.dagId)
                    // When all the DAGs of the scenario are ready, the scenario is marked ready.
                    if (scenarios.containsKey(feedback.scenarioId)
                        && size == scenarios[feedback.scenarioId]!!.directedAcyclicGraphs.size
                    ) {
                        startedDagsByScenario.remove(feedback.scenarioId)
                        onScenarioStarted(feedback.campaignId, feedback.scenarioId)
                    }
                }
            }
        }
    }

    /**
     * Mark the scenario ready and triggers [onAllScenariosReady] if all the others are ready.
     */
    private suspend fun onScenarioStarted(campaignId: CampaignId, scenarioId: ScenarioId) {
        log.debug("Campaign ${campaignId}, scenario ${scenarioId} - All minions were created")
        startedScenarios.add(scenarioId)
        if (startedScenarios.size == scenarios.keys.size) {
            onAllScenariosStarted(campaignId)
        }
    }

    /**
     * Trigger the ramp-up when all the minions for all the scenarios are ready.
     */
    private suspend fun onAllScenariosStarted(campaignId: CampaignId) {
        log.info("All minions for all the scenarios were created, now starting the campaign")
        startedDagsByScenario.clear()
        directivesInProgress.clear()

        startedScenarios.forEach { scenarioId ->
            val directive = MinionsRampUpPreparationDirective(campaignId, scenarioId, startOffsetMs, speedFactor)
            directivesInProgress[directive.key] = DirectiveInProgress(directive)
            directiveProducer.publish(directive)
        }
        startedScenarios.clear()
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
            @Suppress("UNCHECKED_CAST")
            return directive as U
        }
    }
}
