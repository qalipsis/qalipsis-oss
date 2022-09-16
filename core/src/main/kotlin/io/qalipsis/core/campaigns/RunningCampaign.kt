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

package io.qalipsis.core.campaigns

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.Serializable
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty

/**
 * Configuration of a running campaign in QALIPSIS.
 *
 * All the fields are non-nullable for serialization purpose.
 *
 * @property tenant reference of the tenant owning the campaign
 * @property key unique identifier of the campaign
 * @property speedFactor acceleration factor of the ramp-up
 * @property startOffsetMs time to wait until the campaign warm-up and the start of the first minions
 * @property timeoutSinceEpoch actual instant when the timeout should occur
 * @property hardTimeout whether the timeout abortion should done hardly and generate a failure, or not
 * @property scenarios configuration of each scenario
 * @property factories configuration of each factory implied in the campaign
 * @property broadcastChannel channel to use to send a message to all the factories involved in the campaign
 * @property feedbackChannel channel to use to send a feedback to the head
 * @property message optional informational or error message, if not empty
 */
@Serializable
data class RunningCampaign(
    val tenant: String = "",
    val key: CampaignKey,
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
    val hardTimeout: Boolean = false,
    val scenarios: Map<ScenarioName, ScenarioConfiguration> = emptyMap()
) {

    var timeoutSinceEpoch: Long = Long.MAX_VALUE

    val factories: MutableMap<NodeId, FactoryConfiguration> = mutableMapOf()

    lateinit var broadcastChannel: String

    lateinit var feedbackChannel: String

    var message: String = ""

    operator fun contains(factory: NodeId) = !factories[factory]?.assignment.isNullOrEmpty()

    /**
     * Removes the factory from the ones assigned to the factory.
     */
    fun unassignFactory(factory: NodeId) {
        factories.remove(factory)
    }

    /**
     * Removes the assigned scenario from the factory.
     * If the factory has no longer assignment, it is also unassigned.
     */
    fun unassignScenarioOfFactory(scenario: ScenarioName, factory: NodeId) {
        factories[factory]?.let { it ->
            it.assignment.remove(scenario)
            if (it.assignment.isEmpty()) {
                factories.remove(factory)
            }
        }
    }
}

@Serializable
data class ScenarioConfiguration(
    val minionsCount: Int
)

@Serializable
data class FactoryConfiguration(
    val unicastChannel: String,
    val assignment: MutableMap<ScenarioName, FactoryScenarioAssignment> = mutableMapOf()
)

/**
 * Assignment of DAGs of a scenario to a given factory.
 *
 * @property scenarioName name of the scenario
 * @property dags names of the directed acyclic graphs assigned to the factory
 * @property maximalMinionCount maximal count of minions the factory can run for this scenario
 */
@Serializable
data class FactoryScenarioAssignment(
    val scenarioName: ScenarioName,
    @field:NotEmpty
    val dags: Collection<DirectedAcyclicGraphName>,
    @field:Min(1)
    val maximalMinionCount: Int = Int.MAX_VALUE
)