package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario

/**
 * Global registry of all the scenarios available in the context.
 *
 * @author Eric Jessé
 */
interface ScenarioRegistry {

    operator fun contains(scenarioId: ScenarioId): Boolean

    operator fun get(scenarioId: ScenarioId): Scenario?

    operator fun get(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph?

    fun add(scenario: Scenario)

    fun all(): Collection<Scenario>

}