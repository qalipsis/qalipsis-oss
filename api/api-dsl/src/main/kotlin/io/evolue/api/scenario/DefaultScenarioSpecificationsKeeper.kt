package io.evolue.api.scenario

import io.evolue.api.context.ScenarioId

/**
 * Default implementation of the [ScenarioSpecificationsKeeper].
 *
 * @author Eric Jessé
 */
internal class DefaultScenarioSpecificationsKeeper : ScenarioSpecificationsKeeper {

    override fun asMap(): Map<ScenarioId, ReadableScenarioSpecification> {
        return scenariosSpecifications
    }

}