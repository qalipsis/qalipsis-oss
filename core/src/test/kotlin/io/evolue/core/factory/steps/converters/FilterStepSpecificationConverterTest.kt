package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.FilterStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.FilterStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FilterStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = FilterStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<FilterStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = FilterStepSpecificationConverter()

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Boolean = { _ -> true }
        val spec = FilterStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = FilterStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FilterStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertTrue(it is FilterStep)
            assertThat(it).all {
                isInstanceOf(FilterStep::class)
                prop("retryPolicy").isSameAs(spec.retryPolicy)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Boolean = { _ -> true }
        val spec = FilterStepSpecification(blockSpecification)

        val mockedRetryPolicy: RetryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk {
            every { scenario.defaultRetryPolicy } returns mockedRetryPolicy
        }, spec)

        val converter = FilterStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FilterStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(FilterStep::class)
                prop("retryPolicy").isSameAs(mockedRetryPolicy)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }
}