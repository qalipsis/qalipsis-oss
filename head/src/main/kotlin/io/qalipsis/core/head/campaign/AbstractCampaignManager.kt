package io.qalipsis.core.head.campaign

import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.directives.CampaignManagementDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/**
 * Service in charge of keeping track of the campaigns executions across the whole cluster.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignManager(
    private val feedbackHeadChannel: FeedbackHeadChannel,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val directiveProducer: DirectiveProducer,
    private val factoryService: FactoryService,
    private val assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    private val campaignService: CampaignService,
    private val idGenerator: IdGenerator,
    private val campaignReportStateKeeper: CampaignReportStateKeeper
) : CampaignManager, DirectiveProcessor<Directive> {

    private var feedbackConsumptionJob: Job? = null

    @PostConstruct
    fun init() {
        feedbackConsumptionJob = coroutineScope.launch {
            kotlin.runCatching {
                log.debug { "Consuming from $feedbackHeadChannel" }
                feedbackHeadChannel.onReceive("${this@AbstractCampaignManager::class.simpleName}") { feedback ->
                    processFeedback(feedback)
                }
            }
        }
    }

    @PreDestroy
    fun destroy() {
        feedbackConsumptionJob?.cancel()
    }

    override fun accept(directive: Directive) = directive is CampaignManagementDirective

    protected open suspend fun processFeedback(feedback: Feedback) {
        if (feedback is CampaignManagementFeedback) {
            tryAndLog(log) {
                val campaignState = get(feedback.campaignId).process(feedback)
                val directives = campaignState.init(factoryService, campaignReportStateKeeper, idGenerator)
                set(campaignState)
                directives.forEach {
                    directiveProducer.publish(it)
                }
            }
        }
    }

    override suspend fun process(directive: Directive) {
        tryAndLog(log) {
            val campaignState = get((directive as CampaignManagementDirective).campaignId).process(directive)
            val directives = campaignState.init(factoryService, campaignReportStateKeeper, idGenerator)
            set(campaignState)
            directives.forEach {
                directiveProducer.publish(it)
            }
        }
    }

    override suspend fun start(campaign: CampaignConfiguration) {
        val selectedScenarios = campaign.scenarios.keys.toSet()
        val scenarios = factoryService.getActiveScenarios(selectedScenarios).distinctBy { it.id }
        val missingScenarios = selectedScenarios - scenarios.map { it.id }.toSet()
        require(missingScenarios.isEmpty()) { "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories" }

        val factories = factoryService.getAvailableFactoriesForScenarios(selectedScenarios)
        if (factories.isNotEmpty()) {
            selectedScenarios.forEach {
                campaignReportStateKeeper.start(campaign.id, it)
            }
            campaignService.save(campaign)
            try {
                // Locks all the factories from a concurrent assignment resolution.
                factoryService.lockFactories(campaign, factories.map(Factory::nodeId))
                val assignments = assignmentResolver.resolveFactoriesAssignments(campaign, factories, scenarios)

                // Releases the unassigned factories to make them available for other campaigns.
                factoryService.releaseFactories(campaign, (factories.map(Factory::nodeId) - assignments.rowKeySet()))

                val factoriesByNodeId = factories.associateBy(Factory::nodeId)
                assignments.rowMap().forEach { (factoryNodeId, assignments) ->
                    campaign.factories[factoryNodeId] = FactoryConfiguration(
                        unicastChannel = factoriesByNodeId[factoryNodeId]!!.unicastChannel,
                        assignment = assignments
                    )
                }

                val campaignStartState = create(campaign)
                val directives = campaignStartState.init(factoryService, campaignReportStateKeeper, idGenerator)
                set(campaignStartState)
                directives.forEach {
                    directiveProducer.publish(it)
                }
            } catch (e: Exception) {
                campaignService.close(campaign.id, ExecutionStatus.FAILED)
                throw e
            }
        }
    }

    abstract suspend fun create(
        campaign: CampaignConfiguration
    ): CampaignExecutionState

    abstract suspend fun get(campaignId: CampaignId): CampaignExecutionState

    abstract suspend fun set(state: CampaignExecutionState)

    companion object {
        @JvmStatic
        private val log = logger()

    }
}