/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            .shouldSatisfy {
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
