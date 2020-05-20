package io.evolue.core.factory.orchestration

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import java.util.concurrent.ConcurrentHashMap

/**
 * <p>
 * The ScenariosKeeper is composed of a registry to keep the full description of all the scenarios supported by
 * the factory as well as an analyzer in charge of decomposing the scenarios when it receives a directive
 * for it from the head.
 * </p>
 *
 * <p>
 * The decomposition of the scenario (requested from the head as a reference to a directive) is then shared to
 * all the factories via messaging and kept in each registry.
 * </p>
 */
internal class ScenariosKeeper {

    /**
     * Collection of DAGs accessible by scenario and DAG ID.
     */
    private val dagsByScenario: MutableMap<ScenarioId, MutableMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>> =
        ConcurrentHashMap()

    private val scenarios: MutableMap<ScenarioId, Scenario> = ConcurrentHashMap()

    fun hasScenario(scenarioId: ScenarioId): Boolean = scenarios.containsKey(scenarioId)

    fun getScenario(scenarioId: ScenarioId): Scenario? = scenarios[scenarioId]

    fun hasDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): Boolean =
        dagsByScenario[scenarioId]?.containsKey(dagId) ?: false

    fun getDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph? =
        dagsByScenario[scenarioId]?.get(dagId)

}