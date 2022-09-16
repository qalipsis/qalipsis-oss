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
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
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
     * @param RunningCampaign  configuration of the starting campaign
     * @param factories collection of [Factory] available to execute the campaign
     * @param scenarios collection of [ScenarioSummary] to execute in the campaign
     */
    fun resolveFactoriesAssignments(
        runningCampaign: RunningCampaign,
        factories: Collection<Factory>,
        scenarios: Collection<ScenarioSummary>
    ): Table<NodeId, ScenarioName, FactoryScenarioAssignment>

}