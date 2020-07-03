package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.states.SharedStateRegistry
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.UnshelveStepSpecification
import io.evolue.core.factory.steps.SingularUnshelveStep
import io.evolue.core.factory.steps.UnshelveStep
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
internal class UnUnshelveStepSpecificationConverterTest {
    @Test
    internal fun `should support expected spec`() {
        // given
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when+then
        assertTrue(converter.support(relaxedMockk<UnshelveStepSpecification<*, *>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val keys = listOf("value-1", "value-2")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, true, false)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when
        runBlocking {
            converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(UnshelveStep::class)
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("names").isSameAs(keys)
                prop("delete").isEqualTo(true)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val keys = listOf("value-1", "value-2")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, false, false)
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when
        runBlocking {
            converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(UnshelveStep::class)
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("names").isSameAs(keys)
                prop("delete").isEqualTo(false)
            }
        }
    }

    @Test
    internal fun `should convert singular spec without name to step`() {
        // given
        val keys = listOf("value-1")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, true, true)
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when
        runBlocking {
            converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(SingularUnshelveStep::class)
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("name").isEqualTo("value-1")
                prop("delete").isEqualTo(true)
            }
        }
    }
}
