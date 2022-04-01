package io.qalipsis.core.head.orchestration

import com.google.common.collect.Table
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryScenarioAssignment
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.model.Factory

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
    ): Table<NodeId, ScenarioName, FactoryScenarioAssignment>

}