package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.mockk.every
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.MapStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.MapStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class MapStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MapStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<MapStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: ((input: Int) -> String) = { value -> value.toString() }
        val spec = MapStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<MapStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(MapStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(spec.retryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: ((input: Int) -> String) = { value -> value.toString() }
        val spec = MapStepSpecification(blockSpecification)

        val mockedRetryPolicy: RetryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk {
            every { scenario.defaultRetryPolicy } returns mockedRetryPolicy
        }, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<MapStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(MapStep::class).all {
            prop("id").isNotNull()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

}
