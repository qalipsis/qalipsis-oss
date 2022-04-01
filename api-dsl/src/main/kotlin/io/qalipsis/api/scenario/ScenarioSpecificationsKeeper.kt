package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioName

/**
 * Service providing an access to the scenario specifications to load at startup.
 *
 * @author Eric Jessé
 */
interface ScenarioSpecificationsKeeper {

    /**
     * Clears the scenario specifications in the factory.
     */
    fun clear()

    /**
     * Returns the map of specifications in the factory.
     */
    fun asMap(): Map<ScenarioName, ConfiguredScenarioSpecification>
}