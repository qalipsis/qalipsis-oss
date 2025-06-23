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

package io.qalipsis.api.meter.specification

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.steps.GaugeMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.gauge
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class GaugeMeterStepSpecificationImplTest {

    @Test
    internal fun `should add gauge step without failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.gauge(name = "test-gauge", specification)

        assertThat(
            GaugeMeterStepSpecificationImpl(
                "test-gauge",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }


    @Test
    internal fun `should add gauge step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.gauge(name = "test-gauge", specification)
            .shouldFailWhen {
                value.isEqual(13.0)
            }

        assertThat(
            GaugeMeterStepSpecificationImpl(
                "test-gauge",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }

}
