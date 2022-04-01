package io.qalipsis.api.campaign

import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.Serializable
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty

@Serializable
data class CampaignConfiguration(
    val name: CampaignName,
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
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