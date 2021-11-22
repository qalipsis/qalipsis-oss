package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.every
import io.qalipsis.api.context.StepError
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.ValidationStepSpecification
import io.qalipsis.core.factory.steps.ValidationStep
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
    internal fun `should convert spec with name and retry policy to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        val spec = ValidationStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<ValidationStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ValidationStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(spec.retryPolicy)
            prop("specification").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        val spec = ValidationStepSpecification(blockSpecification)

        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<ValidationStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ValidationStep::class).all {
            prop(ValidationStep<*>::id).isEmpty()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("specification").isSameAs(blockSpecification)
        }
    }

}
