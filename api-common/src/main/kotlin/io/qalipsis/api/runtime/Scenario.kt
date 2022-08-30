/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.runtime

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step

/**
 * A [Scenario] represents a full chain of [DirectedAcyclicGraph]s containing all the steps to perform.
 *
 * @property name name of the scenario
 * @property executionProfile defines how the minions should be started when executing the scenario
 * @property defaultRetryPolicy defines on the steps of the scenario when none is explicitly set
 * @property minionsCount default minions count to run the scenario when runtime factor is 1
 * @property dags collection of the [DirectedAcyclicGraph] of the scenario
 */
interface Scenario {
    val name: ScenarioName
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
    suspend fun start(campaignKey: CampaignKey)

    /**
     * Stops a running campaign on the scenario.
     */
    suspend fun stop(campaignKey: CampaignKey)

    /**
     * Destroys the scenario and all its components recursively.
     */
    fun destroy()
}
