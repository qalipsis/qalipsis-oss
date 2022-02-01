package io.qalipsis.core.head.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.campaign.AbstractCampaignManager
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.EmptyState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * @property coroutineScope scope to use to start the coroutines in the context of the campaign orchestration
 * @property idGenerator id generator.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class StandaloneCampaignManager(
    feedbackHeadChannel: FeedbackHeadChannel,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope,
    directiveProducer: DirectiveProducer,
    factoryService: FactoryService,
    assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    campaignService: CampaignService,
    idGenerator: IdGenerator,
    private val campaignReportStateKeeper: CampaignReportStateKeeper
) : AbstractCampaignManager(
    feedbackHeadChannel,
    coroutineScope,
    directiveProducer,
    factoryService,
    assignmentResolver,
    campaignService,
    idGenerator,
    campaignReportStateKeeper
) {

    private var currentCampaignState: CampaignExecutionState = EmptyState

    private val mutex = Mutex()

    override suspend fun start(campaign: CampaignConfiguration) {
        require(currentCampaignState.isCompleted) { "A campaign is already running, please wait for its completion or cancel it" }
        super.start(campaign)
    }

    override suspend fun process(directive: Directive) {
        mutex.withLock { super.process(directive) }
    }

    override suspend fun processFeedback(feedback: Feedback) {
        mutex.withLock { super.processFeedback(feedback) }
    }

    override suspend fun create(
        campaign: CampaignConfiguration
    ): CampaignExecutionState {
        return FactoryAssignmentState(campaign)
    }

    override suspend fun get(campaignId: CampaignId): CampaignExecutionState {
        return currentCampaignState
    }

    override suspend fun set(state: CampaignExecutionState) {
        currentCampaignState = state
    }

}
