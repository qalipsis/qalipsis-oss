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
import io.qalipsis.api.meters.steps.TimerMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.timer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author Francisca Eze
 */
internal class TimerMeterStepSpecificationImplTest {

    @Test
    internal fun `should add timer step without failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Duration =
            { _, _ -> Duration.of(1800000, ChronoUnit.MILLIS) }
        previousStep.timer(name = "my-test-timer", specification)

        assertThat(
            TimerMeterStepSpecificationImpl(
                "my-test-timer",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }


    @Test
    internal fun `should add timer step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Duration =
            { _, _ -> Duration.of(1800000, ChronoUnit.MILLIS) }
        previousStep.timer(name = "my-test-timer", specification)
            .shouldFailWhen {
                max.isGreaterThan(Duration.of(2, ChronoUnit.SECONDS))
                mean.isLessThan(Duration.of(1, ChronoUnit.SECONDS))
                percentile(45.0).isEqual(Duration.ofMillis(300))
                percentile(25.0).isLessThanOrEqual(Duration.ofMillis(1000))
            }

        assertThat(
            TimerMeterStepSpecificationImpl(
                "my-test-timer",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }

}
