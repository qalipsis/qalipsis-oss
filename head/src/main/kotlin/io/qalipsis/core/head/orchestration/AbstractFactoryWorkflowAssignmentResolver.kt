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

import com.google.common.collect.Table
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.model.Factory

/**
 * Abstract implementation of [FactoryWorkflowAssignmentResolver] to cover the common logic of assigning
 * and releasing the unused factories.
 *
 * @author Eric Jessé
 */
abstract class AbstractFactoryWorkflowAssignmentResolver(
    private val factoryService: FactoryService
) : FactoryWorkflowAssignmentResolver {

    override suspend fun assignFactories(
        runningCampaign: RunningCampaign,
        factories: Collection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Collection<Factory> {
        if (factories.isNotEmpty()) {
            factoryService.lockFactories(runningCampaign, factories.map(Factory::nodeId))
        }
        val mutableFactories = factories.toMutableList()
        val assignments = doResolveFactoriesAssignments(runningCampaign, mutableFactories, scenarios)
        // Locks the additional factories.
        val newFactories = mutableFactories.map(Factory::nodeId) - factories.map(Factory::nodeId).toSet()
        if (newFactories.isNotEmpty()) {
            factoryService.lockFactories(runningCampaign, newFactories)
        }
        // Releases the unassigned factories to make them available for other campaigns.
        val discardedFactories = factories.map(Factory::nodeId) - assignments.rowKeySet()
        if (discardedFactories.isNotEmpty()) {
            factoryService.releaseFactories(runningCampaign, discardedFactories)
        }

        // Save the assignments into the campaign.
        val factoriesByNodeId = mutableFactories.associateBy(Factory::nodeId)
        assignments.rowMap().forEach { (factoryNodeId, assignments) ->
            runningCampaign.factories[factoryNodeId] = FactoryConfiguration(
                unicastChannel = factoriesByNodeId[factoryNodeId]!!.unicastChannel,
                assignment = assignments
            )
        }

        return mutableFactories
    }

    protected abstract fun doResolveFactoriesAssignments(
        runningCampaign: RunningCampaign,
        factories: MutableCollection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Table<NodeId, ScenarioName, FactoryScenarioAssignment>
}