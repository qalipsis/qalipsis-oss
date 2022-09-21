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
import org.apache.commons.lang3.RandomStringUtils

/**
 * Class to use to create a new scenario specification for testing purpose.
 */
object TestScenarioFactory {

    private val scenarioCreationNameField = Class.forName("io.qalipsis.api.scenario.ClasspathScenarioInitializer")
        .getDeclaredField("CURRENT_SCENARIO_NAME").also { it.trySetAccessible() }

    fun scenario(
        name: ScenarioName = RandomStringUtils.randomAlphabetic(8),
        configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }
    ): StartScenarioSpecification {
        scenarioCreationNameField.set(null, name)
        val scenario = scenario(configuration)
        scenarioCreationNameField.set(null, null)
        return scenario
    }

}
