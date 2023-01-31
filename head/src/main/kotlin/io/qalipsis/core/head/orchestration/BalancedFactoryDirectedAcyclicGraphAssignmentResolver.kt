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

package io.qalipsis.core.head.orchestration

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.factory.FactoryService
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
internal class BalancedFactoryDirectedAcyclicGraphAssignmentResolver(
    factoryService: FactoryService
) : AbstractFactoryDirectedAcyclicGraphAssignmentResolver(factoryService) {

    override fun doResolveFactoriesAssignments(
        runningCampaign: RunningCampaign,
        factories: Collection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Table<NodeId, ScenarioName, FactoryScenarioAssignment> {
        val scenariosConfiguration = scenarios
            .associate { scenario ->
                val configurationFromCampaign = runningCampaign.scenarios[scenario.name]
                val scenarioMinionsCount = configurationFromCampaign?.minionsCount ?: scenario.minionsCount

                scenario.name to FactoryScenarioAssignment(
                    scenario.name,
                    scenario.directedAcyclicGraphs.map(DirectedAcyclicGraphSummary::name),
                    ceil(scenarioMinionsCount.toDouble() / factories.size).toInt()
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