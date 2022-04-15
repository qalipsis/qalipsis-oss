package io.qalipsis.core.head.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.head.campaign.AbstractCampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.EmptyState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Singleton

/**
 * Component to manage a new Campaign for all the known scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_HEAD])
internal class StandaloneCampaignManager(
    headChannel: HeadChannel,
    factoryService: FactoryService,
    assignmentResolver: FactoryDirectedAcyclicGraphAssignmentResolver,
    campaignService: CampaignService,
    campaignReportStateKeeper: CampaignReportStateKeeper,
    headConfiguration: HeadConfiguration,
    campaignExecutionContext: CampaignExecutionContext,
) : AbstractCampaignManager<CampaignExecutionContext>(
    headChannel,
    factoryService,
    assignmentResolver,
    campaignService,
    campaignReportStateKeeper,
    headConfiguration,
    campaignExecutionContext
) {

    @KTestable
    private var currentCampaignState: CampaignExecutionState<CampaignExecutionContext> = EmptyState

    override suspend fun start(campaign: CampaignConfiguration) {
        require(currentCampaignState.isCompleted) { "A campaign is already running, please wait for its completion or cancel it" }
        super.start(campaign)
    }

    override suspend fun create(
        campaign: CampaignConfiguration
    ): CampaignExecutionState<CampaignExecutionContext> {
        return FactoryAssignmentState(campaign)
    }

    override suspend fun get(
        tenant: String,
        campaignName: CampaignName
    ): CampaignExecutionState<CampaignExecutionContext> {
        return currentCampaignState
    }

    override suspend fun set(state: CampaignExecutionState<CampaignExecutionContext>) {
        currentCampaignState = state
    }

}
