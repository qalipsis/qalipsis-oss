package io.qalipsis.core.factories.steps.converters

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
import io.qalipsis.core.factories.steps.MapWithContextStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
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
            prop(MapWithContextStep<*, *>::id).isEqualTo("my-step")
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
            prop(MapWithContextStep<*, *>::id).isEmpty()
            prop(MapWithContextStep<*, *>::retryPolicy).isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

}
