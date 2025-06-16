/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.runtime

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import java.time.Instant

/**
 * A [Scenario] represents a full chain of [DirectedAcyclicGraph]s containing all the steps to perform.
 *
 * @property name name of the scenario
 * @property description description of the scenario
 * @property version version of the scenario
 * @property builtAt instant when the scenario was built
 * @property executionProfile defines how the minions should be started when executing the scenario
 * @property defaultRetryPolicy defines on the steps of the scenario when none is explicitly set
 * @property minionsCount default minions count to run the scenario when runtime factor is 1
 * @property dags collection of the [DirectedAcyclicGraph] of the scenario
 */
interface Scenario {
    val name: ScenarioName
    val description: String?
    val version: String
    val builtAt: Instant
    val executionProfile: ExecutionProfile
    val defaultRetryPolicy: RetryPolicy
    val minionsCount: Int
    val dags: Collection<DirectedAcyclicGraph>

    /**
     * Verifies of the scenario contains the DAG with the specified ID.
     */
    operator fun contains(dagId: DirectedAcyclicGraphName): Boolean

    /**
     * Returns the DAG with the specified ID if it exists.
     */
    operator fun get(dagId: DirectedAcyclicGraphName): DirectedAcyclicGraph?

    /**
     * Returns the DAG with the specified ID or create it if it does not yet exist.
     */
    fun createIfAbsent(
        dagId: DirectedAcyclicGraphName,
        dagSupplier: (DirectedAcyclicGraphName) -> DirectedAcyclicGraph
    ): DirectedAcyclicGraph

    /**
     * Adds a step to the scenario.
     */
    suspend fun addStep(dag: DirectedAcyclicGraph, step: Step<*, *>)

    /**
     * Finds a step with the expected ID or suspend until it is created or a timeout of 10 seconds happens.
     */
    suspend fun findStep(stepName: StepName): Pair<Step<*, *>, DirectedAcyclicGraph>?

    /**
     * Starts a new campaign on the scenario.
     */
    suspend fun start(configuration: ScenarioStartStopConfiguration)

    /**
     * Stops a running campaign on the scenario.
     */
    suspend fun stop(configuration: ScenarioStartStopConfiguration)

    /**
     * Destroys the scenario and all its components recursively.
     */
    fun destroy()
}
