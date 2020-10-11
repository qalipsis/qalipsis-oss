package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioId
import javax.inject.Singleton

/**
 * Default implementation of the [ScenarioSpecificationsKeeper].
 *
 * @author Eric Jessé
 */
@Singleton
internal class DefaultScenarioSpecificationsKeeper : ScenarioSpecificationsKeeper {

    override fun asMap(): Map<ScenarioId, ConfiguredScenarioSpecification> {
        return scenariosSpecifications
    }

}
