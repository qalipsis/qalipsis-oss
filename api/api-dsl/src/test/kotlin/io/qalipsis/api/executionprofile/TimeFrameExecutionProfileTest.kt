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
                timeframe(periodInMs = 1, timeFrameInMs = 20)
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