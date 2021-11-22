package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.Scenario

/**
 * Global registry of all the scenarios available in the context.
 *
 * @author Eric Jessé
 */
interface ScenariosRegistry {

    operator fun contains(scenarioId: ScenarioId): Boolean

    operator fun get(scenarioId: ScenarioId): Scenario?

    fun add(scenario: Scenario)

}