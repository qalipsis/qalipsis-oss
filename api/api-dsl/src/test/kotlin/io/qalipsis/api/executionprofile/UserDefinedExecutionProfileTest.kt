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
internal class UserDefinedExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { _, _, _ -> MinionsStartingLine(10, 10) }

        val scenario = TestScenarioFactory.scenario {
            profile {
                define(specification)
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(UserDefinedExecutionProfile(specification))
    }

    @Test
    internal fun `should provide the used defined values until there is no more minions`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount, speedFactor ->
                val period = if (pastPeriodMs == 0L) 200L else pastPeriodMs
                MinionsStartingLine(totalMinionsCount / 3, (period / speedFactor).toLong())
            }
        val executionProfile = UserDefinedExecutionProfile(specification)

        val iterator = executionProfile.iterator(21, 2.0)

        assertEquals(MinionsStartingLine(7, 100), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 25), iterator.next())
        assertEquals(MinionsStartingLine(0, 12), iterator.next())
    }

}