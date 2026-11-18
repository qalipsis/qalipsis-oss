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
import io.qalipsis.api.meters.steps.GaugeMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.GaugeOperation
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
        previousStep.gauge(name = "test-gauge", block = specification)

        assertThat(
            GaugeMeterStepSpecificationImpl(
                "test-gauge",
                GaugeOperation.SET,
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }


    @Test
    internal fun `should add gauge step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.gauge(name = "test-gauge", block = specification)
            .shouldSatisfy {
                value.isEqual(13.0)
            }

        assertThat(
            GaugeMeterStepSpecificationImpl(
                "test-gauge",
                GaugeOperation.SET,
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add gauge step adding the values as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.gauge(name = "test-gauge", operation = GaugeOperation.ADD, block = specification)

        assertThat(
            GaugeMeterStepSpecificationImpl(
                "test-gauge",
                GaugeOperation.ADD,
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }

}
