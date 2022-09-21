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
internal class TimeFrameExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                timeframe(1, 20)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(TimeFrameExecutionProfile(1, 20))
    }

    @Test
    internal fun `should provide adaptive count at constant pace`() {
        val executionProfile = TimeFrameExecutionProfile(10, 35)

        val iterator = executionProfile.iterator(10, 1.0)

        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should provide adaptive count with factor at constant pace`() {
        val executionProfile = TimeFrameExecutionProfile(10, 35)

        val iterator = executionProfile.iterator(10, 2.0)

        assertEquals(MinionsStartingLine(4, 5), iterator.next())
        assertEquals(MinionsStartingLine(4, 5), iterator.next())
        assertEquals(MinionsStartingLine(2, 5), iterator.next())
        assertEquals(MinionsStartingLine(0, 5), iterator.next())
    }

}