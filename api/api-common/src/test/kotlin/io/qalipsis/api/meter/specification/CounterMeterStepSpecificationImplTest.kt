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
            .shouldSatisfy {
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
