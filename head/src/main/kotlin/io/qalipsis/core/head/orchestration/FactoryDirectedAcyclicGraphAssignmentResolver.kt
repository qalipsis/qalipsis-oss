package io.qalipsis.core.head.orchestration

import com.google.common.collect.Table
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.core.head.model.NodeId

/**
 * Service in charge of calculating the directed acyclic graphs to assign to factories in the context of a new campaign.
 *
 * @author Eric Jessé
 */
internal interface FactoryDirectedAcyclicGraphAssignmentResolver {

    /**
     * Calculates the DAGs to assign to each factory for the campaign.
     *
     * @param campaignConfiguration  configuration of the starting campaign
     * @param factories collection of [Factory] available to execute the campaign
     * @param scenarios collection of [ScenarioSummary] to execute in the campaign
     */
    fun resolveFactoriesAssignments(
        campaignConfiguration: CampaignConfiguration,
        factories: Collection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Table<NodeId, ScenarioId, Collection<DirectedAcyclicGraphId>>

}