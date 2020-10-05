package io.evolue.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.FilterStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factories.steps.FilterStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class FilterStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<FilterStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<FilterStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
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
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FilterStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(FilterStep::class)
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(spec.retryPolicy)
            prop("specification").isSameAs(blockSpecification)
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

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FilterStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(FilterStep::class)
            prop("id").isNotNull()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("specification").isSameAs(blockSpecification)
        }
    }
}
