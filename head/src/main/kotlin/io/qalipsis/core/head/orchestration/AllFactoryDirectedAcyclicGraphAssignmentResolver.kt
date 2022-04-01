package io.qalipsis.core.head.orchestration

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryScenarioAssignment
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Factory
import jakarta.inject.Singleton
import kotlin.math.ceil

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
    ): Table<NodeId, ScenarioName, FactoryScenarioAssignment> {
        val scenariosConfiguration = scenarios
            .associate {
                it.name to FactoryScenarioAssignment(
                    it.name,
                    it.directedAcyclicGraphs.map(DirectedAcyclicGraphSummary::name),
                    ceil(it.minionsCount.toDouble() / factories.size).toInt()
                )
            }
        val result = HashBasedTable.create<NodeId, ScenarioName, FactoryScenarioAssignment>()
        factories.forEach { factory ->
            scenariosConfiguration.forEach { (scenarioName, configuration) ->
                result.put(
                    factory.nodeId, scenarioName, configuration
                )
            }
        }
        return result
    }
}