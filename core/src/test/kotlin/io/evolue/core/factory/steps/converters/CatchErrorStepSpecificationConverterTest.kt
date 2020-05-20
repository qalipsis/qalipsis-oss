package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.context.StepError
import io.evolue.api.steps.CatchErrorStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.CatchErrorStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchErrorStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = CatchErrorStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<CatchErrorStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = CatchErrorStepSpecificationConverter()

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: (error: Collection<StepError>) -> Unit = {}
        val spec = CatchErrorStepSpecification<Int>(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = CatchErrorStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CatchErrorStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(CatchErrorStep::class)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: (error: Collection<StepError>) -> Unit = {}
        val spec = CatchErrorStepSpecification<Int>(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = CatchErrorStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CatchErrorStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(CatchErrorStep::class)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }
}