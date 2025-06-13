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
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.steps.StageStepEndSpecification
import io.qalipsis.api.steps.StageStepSpecification
import io.qalipsis.api.steps.StageStepStartSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.steps.StageStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class StageStepStartSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<StageStepSpecificationConverter>() {

    @MockK
    private lateinit var minionsKeeper: MinionsKeeper

    @AfterEach
    internal fun tearDown() {
        converter.getProperty<MutableMap<String, StageStep<*, *>>>("startStepsById").clear()
    }

    @Test
    override fun `should support expected spec`() {
        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<StageStepSpecification<*, *>>()))
        Assertions.assertTrue(converter.support(relaxedMockk<StageStepStartSpecification<*>>()))
        Assertions.assertTrue(converter.support(relaxedMockk<StageStepEndSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert group spec without name nor retry policy to step`() = runBlockingTest {
        // given
        val spec = StageStepStartSpecification<Int>()
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<StageStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(StageStep::class).all {
            prop(StageStep<*, *>::name).isNotNull()
            prop("retryPolicy").isSameInstanceAs(mockedRetryPolicy)
        }
        // The name is set in the spec.
        assertThat(spec.name).isEqualTo(creationContext.createdStep!!.name)
        assertThat(converter).typedProp<Map<String, StageStep<*, *>>>("startStepsById").all {
            hasSize(1)
            key(creationContext.createdStep!!.name).isSameInstanceAs(creationContext.createdStep)
        }
    }

    @Test
    internal fun `should convert group spec with name and retry policy to step`() = runBlockingTest {
        // given
        val spec = StageStepStartSpecification<Int>()
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<StageStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(StageStep::class).all {
            prop(StageStep<*, *>::name).isEqualTo("my-step")
            prop("retryPolicy").isSameInstanceAs(mockedRetryPolicy)
        }
        assertThat(spec.name).isEqualTo("my-step")
        assertThat(converter).typedProp<Map<String, StageStep<*, *>>>("startStepsById").all {
            hasSize(1)
            key("my-step").isSameInstanceAs(creationContext.createdStep)
        }
    }

    @Test
    internal fun `should convert end group spec without name to step`() = runBlockingTest {
        // given
        val startSpec = StageStepStartSpecification<Int>()
        startSpec.scenario = relaxedMockk()
        startSpec.name = "the-group-start-spec"
        val stageStep: StageStep<*, *> = relaxedMockk()
        converter.getProperty<MutableMap<String, StageStep<*, *>>>("startStepsById")["the-group-start-spec"] = stageStep

        val spec = StageStepEndSpecification<Int, String>(startSpec)
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<StageStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(StageStepSpecificationConverter.GroupEndProxy::class)
            .all {
                prop(StageStepSpecificationConverter.GroupEndProxy<*>::name).isNotNull()
                prop("retryPolicy").isNull()
                prop("start").isSameInstanceAs(stageStep)
            }
        assertThat(converter).typedProp<Map<String, StageStep<*, *>>>("startStepsById").isEmpty()
    }

    @Test
    internal fun `should convert end group spec with name to step`() = runBlockingTest {
        // given
        val startSpec = StageStepStartSpecification<Int>()
        startSpec.scenario = relaxedMockk()
        startSpec.name = "the-group-start-spec"
        val stageStep: StageStep<*, *> = relaxedMockk()
        converter.getProperty<MutableMap<String, StageStep<*, *>>>("startStepsById")["the-group-start-spec"] = stageStep

        val spec = StageStepEndSpecification<Int, String>(startSpec)
        spec.name = "my-step"

        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<StageStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(StageStepSpecificationConverter.GroupEndProxy::class)
            .all {
                prop(StageStepSpecificationConverter.GroupEndProxy<*>::name).isNotNull()
                prop("retryPolicy").isNull()
                prop("start").isSameInstanceAs(stageStep)
            }
        assertThat(converter).typedProp<Map<String, StageStep<*, *>>>("startStepsById").isEmpty()
    }
}
