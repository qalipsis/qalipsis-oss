/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.scenario

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

internal class ClasspathScenarioInitializerTest {

    @Test
    fun `given a scenario called by a generated loader when searching the declaring package then the package of the scenario is returned`() {
        val callers = listOf(
            "io.qalipsis.api.scenario.ClasspathScenarioInitializer",
            "io.qalipsis.api.scenario.ScenarioSpecificationKt",
            "com.acme.scenarios.MyScenario\$Nested",
            "io.qalipsis.api.scenariosloader.MyScenario\$\$myScenario",
            "io.qalipsis.api.scenario.ClasspathScenarioInitializer\$Companion"
        )

        assertThat(ClasspathScenarioInitializer.findScenarioDeclaringPackage(callers)).isEqualTo("com.acme.scenarios")
    }

    @Test
    fun `given no generated loader in the call stack when searching the declaring package then nothing is returned`() {
        val callers = listOf("io.qalipsis.api.scenario.ScenarioSpecificationKt", "com.acme.scenarios.MyScenario")

        assertThat(ClasspathScenarioInitializer.findScenarioDeclaringPackage(callers)).isNull()
    }

    @Test
    fun `given a scenario in the root package when searching the declaring package then nothing is returned`() {
        val callers = listOf("MyScenario", "io.qalipsis.api.scenariosloader.MyScenario\$\$myScenario")

        assertThat(ClasspathScenarioInitializer.findScenarioDeclaringPackage(callers)).isNull()
    }
}
