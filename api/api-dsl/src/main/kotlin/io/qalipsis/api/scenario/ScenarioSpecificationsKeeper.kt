package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioId

/**
 * Service providing an access to the scenario specifications to load at startup.
 *
 * @author Eric Jessé
 */
interface ScenarioSpecificationsKeeper {

    fun asMap(): Map<ScenarioId, ConfiguredScenarioSpecification>
}