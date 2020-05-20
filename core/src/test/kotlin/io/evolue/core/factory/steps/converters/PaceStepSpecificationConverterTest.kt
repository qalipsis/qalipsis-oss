package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.steps.PaceStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.PaceStep
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
internal class PaceStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = PaceStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<PaceStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = PaceStepSpecificationConverter()

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: (pastPeriodMs: Long) -> Long = { _ -> 1 }
        val spec = PaceStepSpecification<Int>(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = PaceStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<PaceStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(PaceStep::class)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: (pastPeriodMs: Long) -> Long = { _ -> 1 }
        val spec = PaceStepSpecification<Int>(blockSpecification)

        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = PaceStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<PaceStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(PaceStep::class)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }
}