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

package io.qalipsis.core.campaigns

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
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
 * @property timeoutSinceEpoch actual instant when the timeout should occur, in seconds since epoch
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

    var timeoutSinceEpoch: Long = Long.MIN_VALUE

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

    override fun toString(): String {
        return "RunningCampaign(tenant='$tenant', key='$key', speedFactor=$speedFactor, startOffsetMs=$startOffsetMs, hardTimeout=$hardTimeout, scenarios=$scenarios, timeoutSinceEpoch=$timeoutSinceEpoch, factories=$factories, broadcastChannel='$broadcastChannel', feedbackChannel='$feedbackChannel', message='$message')"
    }


}

@Serializable
data class ScenarioConfiguration(
    val minionsCount: Int,
    val executionProfileConfiguration: ExecutionProfileConfiguration,
    val zones: Map<String, Int> = emptyMap()
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