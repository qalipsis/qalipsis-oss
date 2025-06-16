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

package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.ScenarioSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Polina Bril
 */
@Suppress("UNCHECKED_CAST")
internal class ZipLastStepSpecificationTest {

    @Test
    internal fun `should add zip step with defined secondary step with a name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Long, *> = {
            it.returns(123L).configure {
                name = "my-other-step"
            }
        }
        previousStep.zipLast(on = specification)

        val expected = ZipLastStepSpecification<Unit, Pair<Int, Long>>("my-other-step")
        assertEquals(expected, previousStep.nextSteps[0])
        assertThat(previousStep.scenario.exists("my-other-step")).isTrue()
    }

    @Test
    internal fun `should add zip step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Int, *> = {
            it.returns(123)
        }
        previousStep.zipLast(on = specification)

        val nextStep = previousStep.nextSteps[0]
        assertThat(nextStep).isInstanceOf(ZipLastStepSpecification::class).all {
            prop(ZipLastStepSpecification<*, *>::secondaryStepName).isNotNull()
        }
        assertThat(
            previousStep.scenario.exists((nextStep as ZipLastStepSpecification<*, *>).secondaryStepName)
        ).isTrue()
    }
}
