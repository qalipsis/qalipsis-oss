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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isNotNull
import assertk.assertions.key
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.scenario.catadioptre.filterScenarios
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 *
 * @author Eric Jessé
 */
internal class DefaultScenarioSpecificationsKeeperTest {

    @Test
    internal fun `should return all the scenarios when no selector is set`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.empty())

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return all the scenarios when selector is blank`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.of("   "))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return all the scenarios when selector is a list of blanks`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.of("  ,  "))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return only the selected scenarios`() {
        // given
        val keeper =
            DefaultScenarioSpecificationsKeeper(Optional.of("my-second-scenario,my-f*th-scenario,my-fi???-scenario"))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { },
            "my-fourth-scenario" to relaxedMockk { },
            "my-fifth-scenario" to relaxedMockk { },
            "my-sixth-scenario" to relaxedMockk { }
        )) as Map<ScenarioName, *>

        // then
        assertThat(eligibleScenarios).all {
            hasSize(4)
            key("my-second-scenario").isNotNull()
            key("my-fourth-scenario").isNotNull()
            key("my-fifth-scenario").isNotNull()
            key("my-first-scenario").isNotNull()
        }
    }
}
