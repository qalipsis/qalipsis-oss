package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.lang.alsoWhenNull
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.CampaignStartDirective
import io.qalipsis.core.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.directives.MinionsCreationPreparationDirective
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.reflect.KClass

/**
 * Component to manage a new Campaign for all the known scenarios.
 *
 * The component inherits from [DirectiveProcessor] to be aware of the directive delegated to the factories, the head has to be aware of them.
 *
 * @author Eric Jessé
 *
 * @property feedbackHeadChannel consumer for feedback from directives, coming from the factories
 * @property factoryService provider of scenario metadata
 * @property directiveProducer producer to send directives to the factories
 * @property headConfiguration head configuration.
 * @property idGenerator id generator.
 */
@Singleton
internal class DefaultCampaignManager(
    private val feedbackHeadChannel: FeedbackHeadChannel,
    private val factoryService: FactoryService,
    private val directiveProducer: DirectiveProducer,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val headConfiguration: HeadConfiguration,
    private val idGenerator: IdGenerator
) : CampaignManager, DirectiveProcessor<Directive> {

    @KTestable
    private var currentCampaignConfiguration: CampaignConfiguration? = null

    /**
     * Scenarios to include in the campaign.
     */
    private val scenarios = ConcurrentHashMap<ScenarioId, ScenarioSummary>()

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
        consumptionJob = coroutineScope.launch {
            log.debug { "Consuming from $feedbackHeadChannel" }
            feedbackHeadChannel.onReceive("${this@DefaultCampaignManager::class.simpleName}") { feedback ->
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
    override suspend fun start(
        campaignConfiguration: CampaignConfiguration,
        onCriticalFailure: (String) -> Unit
    ) {
        require(currentCampaignConfiguration == null) { "Only one campaign can be triggered at a time" }
        directivesInProgress.clear()
        this.scenarios.clear()
        readyDagsByScenario.clear()
        readyScenarios.clear()
        startedDagsByScenario.clear()
        startedScenarios.clear()

        this.onCriticalFailure = onCriticalFailure

        currentCampaignConfiguration = campaignConfiguration
        factoryService.getAllScenarios(campaignConfiguration.scenarios).forEach { scenario ->
            this.scenarios[scenario.id] = scenario
            triggerMinionsCreation(campaignConfiguration.id, scenario, campaignConfiguration)
        }
    }

    /**
     * Create the directive to create all the minions for the given scenario.
     */
    private suspend fun triggerMinionsCreation(
        id: CampaignId,
        scenario: ScenarioSummary,
        configuration: CampaignConfiguration
    ) {
        log.debug {
            "Campaign ${id}, scenario ${scenario.id} - creating the directive to create the IDs for all the minions"
        }
        val count =
            if (configuration.minionsCountPerScenario > 0) configuration.minionsCountPerScenario else (scenario.minionsCount * configuration.minionsFactor).toInt()

        readyDagsByScenario[scenario.id] = concurrentSet()
        val directive = MinionsCreationPreparationDirective(id, scenario.id, count, channel = headConfiguration.broadcastChannel, key = idGenerator.short())
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

    @LogInput
    internal suspend fun processFeedBack(feedback: Feedback) {
        when (feedback) {
            is DirectiveFeedback -> processDirectiveFeedback(feedback)
            is CampaignStartedForDagFeedback -> receiveCampaignStartedFeedback(feedback)
        }
    }

    /**
     * Broadcast the received directive feedback to the relevant method.
     */
    private suspend fun processDirectiveFeedback(feedback: DirectiveFeedback) {
        directivesInProgress[feedback.directiveKey]?.let { directiveInProgress ->
            log.trace { "Proceeding with the feedback $feedback of directive ${directiveInProgress}." }
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
        }.alsoWhenNull { log.debug { "The directive with key ${feedback.directiveKey} was not found." } }
    }

    /**
     * Log the feedback from a [MinionsCreationPreparationDirective] or trigger the critical failure action when the feedback is in failure.
     */
    @KTestable
    private fun receivedMinionsCreationPreparationFeedback(
        feedback: DirectiveFeedback,
        directive: MinionsCreationPreparationDirective
    ) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug {
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId} - IDs for all the minions are being created"
            }
            FeedbackStatus.FAILED -> onCriticalFailure(
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId} - IDs for all the minions could not be created: ${feedback.error}"
            )
            FeedbackStatus.COMPLETED -> {
                log.debug { "Scenario ${directive.scenarioId} - IDs for all the minions were created" }
            }
        }
    }

    /**
     * Process the feedback from a [MinionsCreationDirectiveReference].
     */
    private suspend fun receiveMinionsCreationDirectiveFeedback(
        feedback: DirectiveFeedback,
        directive: MinionsCreationDirectiveReference
    ) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug {
                "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions are being created"
            }
            FeedbackStatus.FAILED -> {
                // Prevents the completed feedbacks to be processed.
                readyDagsByScenario.clear()
                readyScenarios.clear()
                // TODO Abort the campaign.
                onCriticalFailure(
                    "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions could not be created: ${feedback.error}"
                )
            }
            FeedbackStatus.COMPLETED -> {
                readyDagsByScenario[directive.scenarioId]?.apply {
                    log.debug {
                        "Campaign ${directive.campaignId}, scenario ${directive.scenarioId}, DAG ${directive.dagId} - All the minions were created"
                    }
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
        log.debug { "Campaign ${campaignId}, scenario ${scenarioId} - All minions were created" }
        readyScenarios.add(scenarioId)
        if (readyScenarios.size == scenarios.keys.size) {
            onAllScenariosReady(campaignId)
        }
    }

    /**
     * Trigger the ramp-up when all the minions for all the scenarios are ready.
     */
    private suspend fun onAllScenariosReady(campaignId: CampaignId) {
        log.info { "All minions for all the scenarios were created, now starting the campaign" }
        readyDagsByScenario.clear()
        directivesInProgress.clear()
        startedDagsByScenario.clear()
        startedScenarios.clear()

        readyScenarios.forEach { scenarioId ->
            startedDagsByScenario[scenarioId] = concurrentSet()
            val directive = CampaignStartDirective(campaignId, scenarioId, channel = headConfiguration.broadcastChannel, key = idGenerator.short())
            directivesInProgress[directive.key] = DirectiveInProgress(directive)
            directiveProducer.publish(directive)
        }
        readyScenarios.clear()
    }

    private suspend fun receiveCampaignStartedFeedback(feedback: CampaignStartedForDagFeedback) {
        when (feedback.status) {
            FeedbackStatus.IN_PROGRESS -> log.debug {
                "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign is being started"
            }
            FeedbackStatus.FAILED -> {
                // Prevents the completed feedbacks to be processed.
                startedDagsByScenario.clear()
                readyScenarios.clear()
                // TODO Abort the campaign.
                onCriticalFailure(
                    "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign could not be started: ${feedback.error}"
                )
            }
            FeedbackStatus.COMPLETED -> {
                startedDagsByScenario[feedback.scenarioId]?.apply {
                    log.debug {
                        "Campaign ${feedback.campaignId}, scenario ${feedback.scenarioId}, DAG ${feedback.dagId} - The campaign is started"
                    }
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
        log.debug { "Campaign $campaignId, scenario $scenarioId - All minions were created" }
        startedScenarios.add(scenarioId)
        if (startedScenarios.size == scenarios.keys.size) {
            onAllScenariosStarted(campaignId)
        }
    }

    /**
     * Trigger the ramp-up when all the minions for all the scenarios are ready.
     */
    private suspend fun onAllScenariosStarted(campaignId: CampaignId) {
        log.info { "All minions for all the scenarios were created, now starting the campaign" }
        startedDagsByScenario.clear()
        directivesInProgress.clear()

        val campaignConfiguration = currentCampaignConfiguration
        currentCampaignConfiguration = null

        startedScenarios.forEach { scenarioId ->
            val directive = MinionsRampUpPreparationDirective(
                campaignId,
                scenarioId,
                campaignConfiguration!!.startOffsetMs,
                campaignConfiguration.speedFactor,
                channel = headConfiguration.broadcastChannel,
                key = idGenerator.short()
            )
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
