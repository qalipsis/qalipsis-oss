package io.evolue.api.scenario

import io.evolue.api.context.ScenarioId
import javax.inject.Singleton

/**
 * Default implementation of the [ScenarioSpecificationsKeeper].
 *
 * @author Eric Jessé
 */
@Singleton
internal class DefaultScenarioSpecificationsKeeper : ScenarioSpecificationsKeeper {

    override fun asMap(): Map<ScenarioId, ReadableScenarioSpecification> {
        return scenariosSpecifications
    }

}
