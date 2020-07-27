package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.evolue.api.context.StepError
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.ValidationStepSpecification
import io.evolue.core.factory.steps.ValidationStep
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
internal class ValidationStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<ValidationStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<ValidationStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        val spec = ValidationStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<ValidationStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(ValidationStep::class)
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(spec.retryPolicy)
            prop("specification").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        val spec = ValidationStepSpecification(blockSpecification)

        val mockedRetryPolicy: RetryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk {
            every { scenario.defaultRetryPolicy } returns mockedRetryPolicy
        }, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<ValidationStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(ValidationStep::class)
            prop("id").isNotNull()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("specification").isSameAs(blockSpecification)
        }
    }

}
