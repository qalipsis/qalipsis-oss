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
internal class ProgressiveVolumeExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                more(periodMs = 1, minionsCountProLaunchAtStart = 2, multiplier = 3.0, maxMinionsCountProLaunch = 4)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(ProgressiveVolumeExecutionProfile(1, 2, 3.0, 4))
    }

    @Test
    internal fun `should increase the volume until the limit`() {
        val executionProfile = ProgressiveVolumeExecutionProfile(50, 2, 2.0, 7)

        val iterator = executionProfile.iterator(16, 1.0)

        assertEquals(MinionsStartingLine(2, 50), iterator.next())
        assertEquals(MinionsStartingLine(4, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(3, 50), iterator.next())
        assertEquals(MinionsStartingLine(0, 50), iterator.next())
    }

    @Test
    internal fun `should increase the volume with factor until the limit`() {
        val executionProfile = ProgressiveVolumeExecutionProfile(50, 2, 2.0, 25)

        val iterator = executionProfile.iterator(50, 2.0)

        assertEquals(MinionsStartingLine(2, 50), iterator.next())
        assertEquals(MinionsStartingLine(8, 50), iterator.next())
        assertEquals(MinionsStartingLine(25, 50), iterator.next())
        assertEquals(MinionsStartingLine(15, 50), iterator.next())
        assertEquals(MinionsStartingLine(0, 50), iterator.next())
    }
}