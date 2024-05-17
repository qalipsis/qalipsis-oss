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

package io.qalipsis.api.executionprofile

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ImmediatelyExecutionProfileTest {

    @Test
    internal fun `the default strategy should be immediately`() {
        val scenario = TestScenarioFactory.scenario {}

        assertThat(scenario).prop("executionProfile").isNotNull().isInstanceOf<ImmediatelyExecutionProfile>()
    }

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                immediately()
            }
        }

        assertThat(scenario).prop("executionProfile").isNotNull().isInstanceOf<ImmediatelyExecutionProfile>()
    }

    @Test
    internal fun `should provide constant count at constant pace`() {
        val executionProfile = ImmediatelyExecutionProfile()

        val iterator = executionProfile.iterator(11, 1.0)

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            lines.add(iterator.next())
        }

        assertThat(lines).all {
            hasSize(1)
            MinionsStartingLine(11, 0)
        }
    }


}