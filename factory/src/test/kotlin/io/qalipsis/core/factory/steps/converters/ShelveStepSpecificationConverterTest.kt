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
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.ShelveStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.ShelveStep
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
internal class ShelveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<ShelveStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var sharedStateRegistry: SharedStateRegistry

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<ShelveStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ShelveStep::class).all {
            prop(ShelveStep<*>::name).isEqualTo("my-step")
            prop("sharedStateRegistry").isSameInstanceAs(sharedStateRegistry)
            prop("specification").isSameInstanceAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ShelveStep::class).all {
            prop(ShelveStep<*>::name).isEmpty()
            prop("sharedStateRegistry").isSameInstanceAs(sharedStateRegistry)
            prop("specification").isSameInstanceAs(blockSpecification)
        }
    }
}
