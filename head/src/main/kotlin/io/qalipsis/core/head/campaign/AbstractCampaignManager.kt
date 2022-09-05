package io.qalipsis.core.head.campaign

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryConfiguration
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.CampaignManagementDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service in charge of keeping track of the campaigns executions across the whole cluster.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignManager<C : CampaignExecutionContext>(
    private val headChannel: HeadChannel,
    private val factoryService: FactoryService,
    private val assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    private val campaignService: CampaignService,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val headConfiguration: HeadConfiguration,
    private val campaignExecutionContext: C,
) : CampaignManager, FeedbackListener<Feedback> {

    private val processingMutex = Mutex()

    override fun accept(feedback: Feedback): Boolean {
        return feedback is CampaignManagementFeedback
    }

    @LogInput
    override suspend fun notify(feedback: Feedback) {
        tryAndLog(log) {
            feedback as CampaignManagementFeedback
            processingMutex.withLock {
                val sourceCampaignState = get(feedback.tenant, feedback.campaignKey)
                log.trace { "Processing $feedback on $sourceCampaignState" }
                val campaignState = sourceCampaignState.process(feedback)
                log.trace { "New campaign state $campaignState" }
                campaignState.inject(campaignExecutionContext)
                val directives = campaignState.init()
                set(campaignState)
                directives.forEach {
                    (it as? CampaignManagementDirective)?.tenant = feedback.tenant
                    headChannel.publishDirective(it)
                }
            }
        }
    }

    override suspend fun start(
        configurer: String,
        campaignDisplayName: String,
        configuration: CampaignConfiguration
    ): Campaign {
        val selectedScenarios = configuration.scenarios.keys.toSet()
        val scenarios =
            factoryService.getActiveScenarios(configuration.tenant, selectedScenarios).distinctBy { it.name }
        val missingScenarios = selectedScenarios - scenarios.map { it.name }.toSet()
        require(missingScenarios.isEmpty()) { "The scenarios ${missingScenarios.joinToString()} were not found or are not currently supported by healthy factories" }

        val factories = factoryService.getAvailableFactoriesForScenarios(configuration.tenant, selectedScenarios)
        require(factories.isNotEmpty()) { "No available factory found to execute the campaign" }

        val createdCampaign = campaignService.create(configurer, campaignDisplayName, configuration)
        selectedScenarios.forEach {
            campaignReportStateKeeper.start(configuration.key, it)
        }
        try {
            log.trace { "Factories to evaluate for campaign ${configuration.key}: ${factories.map(Factory::nodeId)}" }
            // Locks all the factories from a concurrent assignment resolution.
            factoryService.lockFactories(configuration, factories.map(Factory::nodeId))
            val assignments = assignmentResolver.resolveFactoriesAssignments(configuration, factories, scenarios)
            log.trace { "Factory assignment for campaign ${configuration.key}: $assignments" }

            // Releases the unassigned factories to make them available for other campaigns.
            factoryService.releaseFactories(configuration, (factories.map(Factory::nodeId) - assignments.rowKeySet()))

            configuration.broadcastChannel = getBroadcastChannelName(configuration)
            configuration.feedbackChannel = getFeedbackChannelName(configuration)

            val factoriesByNodeId = factories.associateBy(Factory::nodeId)
            assignments.rowMap().forEach { (factoryNodeId, assignments) ->
                configuration.factories[factoryNodeId] = FactoryConfiguration(
                    unicastChannel = factoriesByNodeId[factoryNodeId]!!.unicastChannel,
                    assignment = assignments
                )
            }

            headChannel.subscribeFeedback(configuration.feedbackChannel)
            val campaignStartState = create(configuration)
            log.info { "Starting the campaign $campaignDisplayName with scenarios ${scenarios.map { it.name }} on factories ${configuration.factories.keys}" }
            campaignStartState.inject(campaignExecutionContext)
            processingMutex.withLock {
                val directives = campaignStartState.init()
                set(campaignStartState)
                directives.forEach {
                    (it as? CampaignManagementDirective)?.tenant = configuration.tenant
                    headChannel.publishDirective(it)
                }
            }
            return createdCampaign
        } catch (e: Exception) {
            campaignService.close(configuration.tenant, configuration.key, ExecutionStatus.FAILED)
            throw e
        }
    }

    @LogInput
    override suspend fun abort(aborter: String, tenant: String, campaignKey: String, hard: Boolean) {
        tryAndLog(log) {
            processingMutex.withLock {
                val sourceCampaignState = get(tenant, campaignKey)
                val campaignState = sourceCampaignState.abort(AbortCampaignConfiguration(hard))
                log.trace { "Campaign state $campaignState" }
                campaignState.inject(campaignExecutionContext)
                val directives = campaignState.init()
                campaignService.setAborter(tenant, aborter, campaignKey)
                set(campaignState)
                campaignReportStateKeeper.abort(campaignKey)
                directives.forEach {
                    headChannel.publishDirective(it)
                }
            }
        }
    }

    abstract suspend fun create(
        campaign: CampaignConfiguration
    ): CampaignExecutionState<C>

    @LogInputAndOutput
    abstract suspend fun get(tenant: String, campaignKey: CampaignKey): CampaignExecutionState<C>

    @LogInput
    abstract suspend fun set(state: CampaignExecutionState<C>)

    @LogInput
    protected open suspend fun getBroadcastChannelName(campaign: CampaignConfiguration): String =
        BROADCAST_CONTEXTS_CHANNEL

    @LogInput
    protected open suspend fun getFeedbackChannelName(campaign: CampaignConfiguration): String =
        FEEDBACK_CONTEXTS_CHANNEL

    companion object {

        const val BROADCAST_CONTEXTS_CHANNEL = "directives-broadcast"

        const val FEEDBACK_CONTEXTS_CHANNEL = "feedbacks"

        private val log = logger()

    }
}