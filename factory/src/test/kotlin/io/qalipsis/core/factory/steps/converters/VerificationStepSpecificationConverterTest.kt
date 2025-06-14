/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.VerificationStepSpecification
import io.qalipsis.core.factory.steps.VerificationStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class VerificationStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<VerificationStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<VerificationStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step and ignore the error reporting`() = runBlockingTest {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = VerificationStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.reporting.reportErrors = true
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<VerificationStepSpecification<*, *>>)

        // then
        assertThat(spec.reporting.reportErrors).isTrue()
        assertThat(creationContext.createdStep!!).isInstanceOf(VerificationStep::class).all {
            prop(VerificationStep<*, *>::name).isEqualTo("my-step")
            prop("reportLiveStateRegistry").isSameInstanceAs(campaignReportLiveStateRegistry)
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isSameInstanceAs(meterRegistry)
            prop("assertionBlock").isSameInstanceAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = VerificationStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<VerificationStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(VerificationStep::class).all {
            prop(VerificationStep<*, *>::name).isEmpty()
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isSameInstanceAs(meterRegistry)
            prop("assertionBlock").isSameInstanceAs(blockSpecification)
        }
    }
}
