package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioId

/**
 * Overall interface for a scenario specification, as visible by the scenario developers.
 *
 * Plugins should add extension function to this interface when creating new step specifications eligible at the scenario level.
 *
 * @author Eric Jessé
 */
interface ScenarioSpecification


/**
 * Overall interface for a scenario, as visible by the scenario developers on a newly created scenario.
 *
 * @author Eric Jessé
 */
interface StartScenarioSpecification : ScenarioSpecification {

    /**
     * Specifies the origin of the graph tree, where the load will be injected.
     *
     * This is mandatory on a scenario but can be set only once.
     */
    fun start(): ScenarioSpecification

}

internal val scenariosSpecifications = mutableMapOf<ScenarioId, ConfiguredScenarioSpecification>()

fun scenario(
    name: ScenarioId,
    configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }
): StartScenarioSpecification {
    val scenario = ScenarioSpecificationImplementation(name)
    scenario.configuration()
    scenariosSpecifications[name] = scenario

    return scenario
}
