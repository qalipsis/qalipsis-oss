package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.CatchExhaustedContextStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.CatchExhaustedContextStep
import io.qalipsis.core.factories.steps.VerificationStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class CatchExhaustedContextStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CatchExhaustedContextStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CatchExhaustedContextStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val blockSpecification: suspend (context: StepContext<*, String>) -> Unit = {}
        val spec = CatchExhaustedContextStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(
            creationContext as StepCreationContext<CatchExhaustedContextStepSpecification<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CatchExhaustedContextStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("block").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val blockSpecification: suspend (context: StepContext<*, String>) -> Unit = {}
        val spec = CatchExhaustedContextStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(
            creationContext as StepCreationContext<CatchExhaustedContextStepSpecification<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CatchExhaustedContextStep::class).all {
            prop(CatchExhaustedContextStep<*>::id).isEmpty()
            prop("block").isSameAs(blockSpecification)
        }
    }
}
