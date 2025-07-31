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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.collections.Table
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class LocalAssignmentStoreImpl(
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

    @LogInputAndOutput
    override fun hasMinionsAssigned(scenarioName: ScenarioName): Boolean {
        return (actualAssignments[scenarioName]?.isNotEmpty() == true).also {
            log.trace {
                if (it) {
                    "The scenario $scenarioName has minions running locally"
                } else {
                    "The scenario $scenarioName has no minion running locally"
                }
            }
        }
    }

    @LogInput
    override fun save(scenarioName: ScenarioName, assignments: Map<MinionId, Collection<DirectedAcyclicGraphName>>) {
        assignments.forEach { (minionId, dagsIds) ->
            log.trace { "Marking the DAGs $dagsIds of scenario $scenarioName as executed locally for the minion $minionId" }
            actualAssignments.computeIfAbsent(scenarioName, minionId) { concurrentSet() } += dagsIds
        }
    }

    @LogInputAndOutput
    override fun isLocal(scenarioName: ScenarioName, minionId: MinionId, dagId: DirectedAcyclicGraphName): Boolean {
        return actualAssignments[scenarioName, minionId]?.contains(dagId) ?: false
    }

    @LogInputAndOutput
    override fun hasRootUnderLoadLocally(scenarioName: ScenarioName, minionId: MinionId): Boolean {
        return rootDagsUnderLoad[scenarioName]?.let { dagId ->
            isLocal(scenarioName, minionId, dagId).also {
                log.trace {
                    if (it) {
                        "The minion $minionId is running the root DAG $dagId of the scenario $scenarioName locally"
                    } else {
                        "The minion $minionId is not running the root DAG $dagId of the scenario $scenarioName locally"
                    }
                }
            }
        } ?: false
    }

    @LogInput
    override fun reset() {
        log.trace { "Resetting the local assignments" }
        actualAssignments.clear()
        rootDagsUnderLoad = scenarioRegistry.all()
            .associate { scenario -> scenario.name to scenario.dags.first { it.isRoot && it.isUnderLoad && !it.isSingleton }.name }
    }

    private companion object {
        val log = logger()
    }
}