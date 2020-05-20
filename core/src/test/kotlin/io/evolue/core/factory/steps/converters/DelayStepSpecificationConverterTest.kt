package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.evolue.api.steps.DelayStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.DelayStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = DelayedStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<DelayStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = DelayedStepSpecificationConverter()

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = DelayedStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(DelayStep::class)
                prop("delay").isEqualTo(Duration.ofMillis(123))
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = DelayedStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(DelayStep::class)
                prop("delay").isEqualTo(Duration.ofMillis(123))
            }
        }
    }
}