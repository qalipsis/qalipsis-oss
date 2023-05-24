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
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.every
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.MapWithContextStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.MapWithContextStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("UNCHECKED_CAST")
internal class MapWithContextStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MapWithContextStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<MapWithContextStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() = runBlockingTest {
        // given
        val blockSpecification: ((context: StepContext<Int, String>, input: Int) -> String) =
            { _, value -> value.toString() }
        val spec = MapWithContextStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<MapWithContextStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(MapWithContextStep::class).all {
            prop(MapWithContextStep<*, *>::name).isEqualTo("my-step")
            prop(MapWithContextStep<*, *>::retryPolicy).isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = runBlockingTest {
        // given
        val blockSpecification: ((context: StepContext<Int, String>, input: Int) -> String) =
            { _, value -> value.toString() }
        val spec = MapWithContextStepSpecification(blockSpecification)
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(relaxedMockk(), directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<MapWithContextStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(MapWithContextStep::class).all {
            prop(MapWithContextStep<*, *>::name).isEmpty()
            prop(MapWithContextStep<*, *>::retryPolicy).isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

}
