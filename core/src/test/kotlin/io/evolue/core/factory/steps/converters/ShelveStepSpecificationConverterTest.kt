package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.states.SharedStateRegistry
import io.evolue.api.steps.ShelveStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.ShelveStep
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
internal class ShelveStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = ShelveStepSpecificationConverter(sharedStateRegistry)

        // when+then
        assertTrue(converter.support(relaxedMockk<ShelveStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = ShelveStepSpecificationConverter(sharedStateRegistry)

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { value -> mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = ShelveStepSpecificationConverter(sharedStateRegistry)

        // when
        runBlocking {
            converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(ShelveStep::class)
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { value -> mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = ShelveStepSpecificationConverter(sharedStateRegistry)

        // when
        runBlocking {
            converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(ShelveStep::class)
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("specification").isSameAs(blockSpecification)
            }
        }
    }
}
