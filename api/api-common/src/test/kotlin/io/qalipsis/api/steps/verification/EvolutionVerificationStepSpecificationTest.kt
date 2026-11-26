/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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
package io.qalipsis.api.steps.verification

import assertk.all
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meter.specification.DummyStepSpecification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EvolutionVerificationStepSpecificationTest {

    private val keyExtractor: (StepContext<Unit, Unit>, Unit) -> Any? = { _, _ -> "my-key" }

    private val valueExtractor: (Unit) -> Long? = { 1L }

    @Test
    fun `given no expectation when adding an increasing verification then all the transitions are expected valid`() {
        val previousStep = DummyStepSpecification()

        previousStep.verifyIncreasing("my-step", keyExtractor, valueExtractor)

        assertThat(previousStep.nextSteps.single())
            .isInstanceOf(EvolutionVerificationStepSpecificationImpl::class).all {
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::name).isEqualTo("my-step")
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::direction).isEqualTo(EvolutionDirection.INCREASING)
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::keyExtractor).isSameAs(keyExtractor)
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::valueExtractor).isSameAs(valueExtractor)
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::expectedPercentile).isEqualTo(100.0)
            }
    }

    @Test
    fun `given an expectation when adding a decreasing verification then the percentile is kept`() {
        val previousStep = DummyStepSpecification()

        previousStep.verifyDecreasing("my-step", keyExtractor, valueExtractor).shouldSatisfy { percentile(99.5) }

        assertThat(previousStep.nextSteps.single())
            .isInstanceOf(EvolutionVerificationStepSpecificationImpl::class).all {
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::direction).isEqualTo(EvolutionDirection.DECREASING)
                prop(EvolutionVerificationStepSpecificationImpl<*, *>::expectedPercentile).isEqualTo(99.5)
            }
    }

    @Test
    fun `given a percentile out of range when setting the expectation then it is rejected`() {
        val step = DummyStepSpecification().verifyIncreasing("my-step", keyExtractor, valueExtractor)

        listOf(0.0, 100.1).forEach { percentile ->
            assertThat(assertThrows<IllegalArgumentException> { step.shouldSatisfy { percentile(percentile) } })
                .hasMessage("The percentile should be greater than 0 and less than or equal to 100, but was $percentile")
        }
    }
}
