package io.evolue.api.scenario

import io.evolue.api.context.ScenarioId

/**
 *
 * Service providing the access to the scenario specifications to load at startup.
 *
 * @author Eric Jessé
 */
interface ScenarioSpecificationsKeeper {

    fun asMap(): Map<ScenarioId, ReadableScenarioSpecification>
}