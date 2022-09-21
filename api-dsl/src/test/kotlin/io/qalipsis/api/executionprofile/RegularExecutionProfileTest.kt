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

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class RegularExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                regular(1, 2)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(RegularExecutionProfile(1, 2))
    }

    @Test
    internal fun `should provide constant count at constant pace`() {
        val executionProfile = RegularExecutionProfile(10, 5)

        val iterator = executionProfile.iterator(11, 1.0)

        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(1, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should provide constant count with factor at constant pace`() {
        val executionProfile = RegularExecutionProfile(10, 5)

        val iterator = executionProfile.iterator(11, 2.0)

        assertEquals(MinionsStartingLine(5, 5), iterator.next())
        assertEquals(MinionsStartingLine(5, 5), iterator.next())
        assertEquals(MinionsStartingLine(1, 5), iterator.next())
        assertEquals(MinionsStartingLine(0, 5), iterator.next())
    }

}