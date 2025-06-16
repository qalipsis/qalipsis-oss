/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.scenario

import io.qalipsis.api.services.ServicesFiles
import java.time.Instant
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

/**
 * Overall interface for a scenario specification, as visible by the scenario developers.
 *
 * Plugins should add extension function to this interface when creating new step specifications eligible at the scenario level.
 *
 * @author Eric Jessé
 */
interface ScenarioSpecification {

    /**
     * The number of steps specifications in a step scenario.
     */
    val size: Long

    /**
     * Description of the scenario, optional.
     */
    var description: String?

    /**
     * Version of the scenario.
     */
    var version: String

    /**
     * Timestamp when the scenario was built.
     */
    var builtAt: Instant
}


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
