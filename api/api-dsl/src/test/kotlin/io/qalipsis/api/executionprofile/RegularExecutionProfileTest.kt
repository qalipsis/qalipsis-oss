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
internal class RegularExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                regular(periodMs = 500, minionsCountProLaunch = 20)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(RegularExecutionProfile(500, 20))
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