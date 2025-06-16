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
internal class ImmediateExecutionProfileTest {

    @Test
    internal fun `the default strategy should be immediately`() {
        val scenario = TestScenarioFactory.scenario {}

        assertThat(scenario).prop("executionProfile").isNotNull().isInstanceOf<ImmediateExecutionProfile>()
    }

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                immediate()
            }
        }

        assertThat(scenario).prop("executionProfile").isNotNull().isInstanceOf<ImmediateExecutionProfile>()
    }

    @Test
    internal fun `should provide constant count at constant pace`() {
        val executionProfile = ImmediateExecutionProfile()

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