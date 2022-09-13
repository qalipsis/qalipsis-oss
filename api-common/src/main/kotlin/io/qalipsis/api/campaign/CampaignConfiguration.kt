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

package io.qalipsis.api.campaign

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.Serializable
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty

@Serializable
data class CampaignConfiguration(
    val tenant: String = "",
    val key: CampaignKey,
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
    val timeoutDurationSec: Long? = null,
    val hardTimeout: Boolean? = null,
    val scenarios: Map<ScenarioName, ScenarioConfiguration> = emptyMap()
) {

    val factories: MutableMap<NodeId, FactoryConfiguration> = mutableMapOf()

    lateinit var broadcastChannel: String

    lateinit var feedbackChannel: String

    var message: String? = null

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