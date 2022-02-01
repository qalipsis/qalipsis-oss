package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.head.model.NodeId
import kotlinx.serialization.Serializable

@Serializable
data class CampaignConfiguration(
    val id: CampaignId,
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
    val broadcastChannel: String,
    val scenarios: Map<ScenarioId, ScenarioConfiguration> = emptyMap(),
    val factories: MutableMap<NodeId, FactoryConfiguration> = mutableMapOf()
) {

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
    fun unassignScenarioOfFactory(scenario: ScenarioId, factory: NodeId) {
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
    val assignment: MutableMap<ScenarioId, Collection<DirectedAcyclicGraphId>> = mutableMapOf()
)