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

import io.qalipsis.api.services.ServicesFiles
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

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

/**
 * Interface of service in charge of creating new implementations of [ConfigurableScenarioSpecification] and
 * [StartScenarioSpecification] when the function [scenario] is called in a context of
 * loading the scenarios.
 */
interface ScenarioFactory {

    fun newScenario(): ConfigurableScenarioSpecification

}

/**
 * Dynamically created instance of [ScenarioFactory].
 */
private val scenarioFactory: ScenarioFactory by lazy(SYNCHRONIZED) {
    ConfigurableScenarioSpecification::class.java.classLoader
        .getResourceAsStream("META-INF/services/qalipsis/scenarioFactory")!!.use { stream ->
            val scenarioFactoryClass = ServicesFiles.readFile(stream).first()
            Class.forName(scenarioFactoryClass).getConstructor().newInstance() as ScenarioFactory
        }
}

/**
 * Declares a new scenario to execute into QALIPSIS.
 *
 * @param configuration provides utilities to tweak the newly created scenario.
 */
fun scenario(
    configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }
): StartScenarioSpecification {
    val scenario = scenarioFactory.newScenario()
    scenario.configuration()
    return scenario as StartScenarioSpecification
}
