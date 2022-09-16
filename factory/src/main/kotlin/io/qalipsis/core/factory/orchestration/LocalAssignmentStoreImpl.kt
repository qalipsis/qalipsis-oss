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

package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.collections.Table
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class LocalAssignmentStoreImpl(
    private val scenarioRegistry: ScenarioRegistry
) : LocalAssignmentStore {

    /**
     * Table to track the assignments of the minions in the local factory.
     */
    private val actualAssignments =
        concurrentTableOf<ScenarioName, MinionId, MutableCollection<DirectedAcyclicGraphName>>()

    /**
     * Map of root DAGs under load keyed by their scenarios ID.
     */
    private lateinit var rootDagsUnderLoad: Map<ScenarioName, DirectedAcyclicGraphName>

    override val assignments: Table<ScenarioName, MinionId, out Collection<DirectedAcyclicGraphName>>
        get() = actualAssignments

    override fun hasMinionsAssigned(scenarioName: ScenarioName): Boolean {
        return actualAssignments[scenarioName]?.isNotEmpty() == true
    }

    override fun save(scenarioName: ScenarioName, assignments: Map<MinionId, Collection<DirectedAcyclicGraphName>>) {
        assignments.forEach { (minionId, dagsIds) ->
            actualAssignments.computeIfAbsent(scenarioName, minionId) { concurrentSet() } += dagsIds
        }
    }

    override fun isLocal(scenarioName: ScenarioName, minionId: MinionId, dagId: DirectedAcyclicGraphName): Boolean {
        return actualAssignments[scenarioName, minionId]?.contains(dagId) ?: false
    }

    override fun hasRootUnderLoadLocally(scenarioName: ScenarioName, minionId: MinionId): Boolean {
        return rootDagsUnderLoad[scenarioName]?.let { dagId ->
            isLocal(scenarioName, minionId, dagId)
        } ?: false
    }

    override fun reset() {
        actualAssignments.clear()
        rootDagsUnderLoad = scenarioRegistry.all()
            .associate { scenario -> scenario.name to scenario.dags.first { it.isRoot && it.isUnderLoad && !it.isSingleton }.name }
    }
}