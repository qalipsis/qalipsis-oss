/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.NodeId
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.AbstractCampaignManager
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.EmptyState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.head.orchestration.FactoryDirectedAcyclicGraphAssignmentResolver
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

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
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope,
    campaignExecutionContext: CampaignExecutionContext,
) : AbstractCampaignManager<CampaignExecutionContext>(
    headChannel,
    factoryService,
    assignmentResolver,
    campaignService,
    campaignReportStateKeeper,
    headConfiguration,
    coroutineScope,
    campaignExecutionContext
) {

    /**
     * Current state of the uniquely running campaign.
     */
    @KTestable
    private var currentCampaignState: CampaignExecutionState<CampaignExecutionContext> = EmptyState

    /**
     * Map of the campaigns waiting for the healthy factories, keyed by their nodes.
     */
    @KTestable
    private val awaitingCampaign = ConcurrentHashMap<NodeId, CampaignKey>()

    override suspend fun findAwaitingCampaign(nodeId: NodeId): CampaignKey? {
        return awaitingCampaign.remove(nodeId)
    }

    override suspend fun start(
        tenant: String,
        configurer: String,
        configuration: CampaignConfiguration
    ): RunningCampaign {
        require(currentCampaignState.isCompleted) { "A campaign is already running, please wait for its completion or cancel it" }
        return super.start(tenant, configurer, configuration)
    }

    override suspend fun create(
        campaign: RunningCampaign
    ): CampaignExecutionState<CampaignExecutionContext> {
        return FactoryAssignmentState(campaign)
    }

    override suspend fun get(
        tenant: String,
        campaignKey: CampaignKey
    ): CampaignExecutionState<CampaignExecutionContext> {
        return currentCampaignState
    }

    override suspend fun set(state: CampaignExecutionState<CampaignExecutionContext>) {
        currentCampaignState = state
    }

}
