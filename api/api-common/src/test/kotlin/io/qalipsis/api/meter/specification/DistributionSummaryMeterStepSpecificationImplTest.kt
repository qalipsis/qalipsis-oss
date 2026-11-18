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
import io.qalipsis.api.meters.steps.DistributionSummaryMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.summary
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class DistributionSummaryMeterStepSpecificationImplTest {

    @Test
    internal fun `should add summary step without failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.summary(name = "my-test-summary", specification)

        assertThat(
            DistributionSummaryMeterStepSpecificationImpl(
                "my-test-summary",
                specification
            )
        ).isEqualTo(
            previousStep.nextSteps[0]
        )
    }


    @Test
    internal fun `should add summary step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        previousStep.summary(name = "my-test-summary", specification)
            .shouldSatisfy {
                max.isGreaterThan(12.0)
                mean.isLessThan(14.0)
            }

        assertThat(
            DistributionSummaryMeterStepSpecificationImpl(
                "my-test-summary",
                specification
            )
        ).isEqualTo(
            previousStep.nextSteps[0]
        )
    }

}
