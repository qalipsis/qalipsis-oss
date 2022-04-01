package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario

/**
 * Global registry of all the scenarios available in the context.
 *
 * @author Eric Jessé
 */
interface ScenarioRegistry {

    operator fun contains(scenarioName: ScenarioName): Boolean

    operator fun get(scenarioName: ScenarioName): Scenario?

    operator fun get(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): DirectedAcyclicGraph?

    fun add(scenario: Scenario)

    fun all(): Collection<Scenario>

}