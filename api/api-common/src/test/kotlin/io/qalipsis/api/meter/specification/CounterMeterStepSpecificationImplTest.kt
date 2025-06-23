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
import io.qalipsis.api.meters.steps.CounterMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.counter
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class CounterMeterStepSpecificationImplTest {

    @Test
    internal fun `should add counter step without failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.counter(name = "test-counter", specification)

        assertThat(
            CounterMeterStepSpecificationImpl(
                "test-counter",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }


    @Test
    internal fun `should add counter step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.counter(name = "test-counter", specification)
            .shouldFailWhen {
                count.isLessThan(12.0)

                assertThat(
                    CounterMeterStepSpecificationImpl(
                        "test-counter",
                        specification
                    )
                ).isEqualTo(previousStep.nextSteps[0])
            }

    }
}
