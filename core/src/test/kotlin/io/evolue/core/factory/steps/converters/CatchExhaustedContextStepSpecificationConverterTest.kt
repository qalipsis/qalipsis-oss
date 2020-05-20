package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.context.StepContext
import io.evolue.api.steps.CatchExhaustedContextStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.CatchExhaustedContextStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = CatchExhaustedContextStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<CatchExhaustedContextStepSpecification<*, *>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = CatchExhaustedContextStepSpecificationConverter()

        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }


    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: suspend (context: StepContext<Int, String>) -> Unit = {}
        val spec = CatchExhaustedContextStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = CatchExhaustedContextStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<CatchExhaustedContextStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(CatchExhaustedContextStep::class)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: suspend (context: StepContext<Int, String>) -> Unit = {}
        val spec = CatchExhaustedContextStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = CatchExhaustedContextStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<CatchExhaustedContextStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(CatchExhaustedContextStep::class)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }
}