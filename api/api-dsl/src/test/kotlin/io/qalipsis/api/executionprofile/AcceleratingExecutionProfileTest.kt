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
internal class AcceleratingExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                faster(startPeriodMs = 1, accelerator = 2.0, minPeriodMs = 3, minionsCountProLaunch = 4)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(AcceleratingExecutionProfile(1, 2.0, 3, 4))
    }

    @Test
    internal fun `should accelerate the pace until the limit`() {
        val executionProfile = AcceleratingExecutionProfile(100, 2.0, 10, 4)

        val iterator = executionProfile.iterator(22, 1.0)

        assertEquals(MinionsStartingLine(4, 100), iterator.next())
        assertEquals(MinionsStartingLine(4, 50), iterator.next())
        assertEquals(MinionsStartingLine(4, 25), iterator.next())
        assertEquals(MinionsStartingLine(4, 12), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should accelerate the pace with factor until the limit`() {
        val executionProfile = AcceleratingExecutionProfile(100, 2.0, 10, 4)

        val iterator = executionProfile.iterator(22, 2.0)

        assertEquals(MinionsStartingLine(4, 100), iterator.next())
        assertEquals(MinionsStartingLine(4, 25), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }
}