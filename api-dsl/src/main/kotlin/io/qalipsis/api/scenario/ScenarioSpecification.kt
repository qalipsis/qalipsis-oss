/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.scenario

import io.qalipsis.api.context.ScenarioName

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

internal val scenariosSpecifications = mutableMapOf<ScenarioName, ConfiguredScenarioSpecification>()

fun scenario(
    name: ScenarioName,
    configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }
): StartScenarioSpecification {
    val scenario = ScenarioSpecificationImplementation(name)
    scenario.configuration()
    scenariosSpecifications[name] = scenario

    return scenario
}
