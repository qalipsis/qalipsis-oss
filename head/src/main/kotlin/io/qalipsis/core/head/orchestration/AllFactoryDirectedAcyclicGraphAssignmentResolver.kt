package io.qalipsis.core.head.orchestration

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Factory
import jakarta.inject.Singleton

/**
 * Default implementation of [FactoryDirectedAcyclicGraphAssignmentResolver] that simply associates all the DAGs of all the minions
 * to all the factories.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(missingBeans = [FactoryDirectedAcyclicGraphAssignmentResolver::class])
)
internal class AllFactoryDirectedAcyclicGraphAssignmentResolver : FactoryDirectedAcyclicGraphAssignmentResolver {

    override fun resolveFactoriesAssignments(
        campaignConfiguration: CampaignConfiguration,
        factories: Collection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Table<NodeId, ScenarioId, Collection<DirectedAcyclicGraphId>> {
        val scenariosAndDags = scenarios
            .associateWith { it.directedAcyclicGraphs.map(DirectedAcyclicGraphSummary::id) }
            .mapKeys { it.key.id }
        val result = HashBasedTable.create<NodeId, ScenarioId, Collection<DirectedAcyclicGraphId>>()
        factories.forEach { factory ->
            scenariosAndDags.forEach { (scenarioId, dagsIds) -> result.put(factory.nodeId, scenarioId, dagsIds) }
        }
        return result
    }
}