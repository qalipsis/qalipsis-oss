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

import io.qalipsis.api.context.ScenarioName
import org.apache.commons.lang3.RandomStringUtils

/**
 * Class to use to create a new scenario specification for testing purpose.
 */
object TestScenarioFactory {

    private val scenarioCreationNameField = Class.forName("io.qalipsis.api.scenario.ClasspathScenarioInitializer")
        .getDeclaredField("CURRENT_SCENARIO_NAME").also { it.trySetAccessible() }

    fun scenario(
        name: ScenarioName = RandomStringUtils.secure().nextAlphabetic(8),
        configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }
    ): StartScenarioSpecification {
        scenarioCreationNameField.set(null, name)
        val scenario = scenario(configuration)
        scenarioCreationNameField.set(null, null)
        return scenario
    }

}
