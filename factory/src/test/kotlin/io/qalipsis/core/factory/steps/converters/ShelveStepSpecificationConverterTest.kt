package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.ShelveStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.ShelveStep
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
internal class ShelveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<ShelveStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var sharedStateRegistry: SharedStateRegistry

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<ShelveStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ShelveStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
            prop("specification").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val blockSpecification: (input: String) -> Map<String, Any?> = { mapOf() }
        val spec = ShelveStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<ShelveStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ShelveStep::class).all {
            prop(ShelveStep<*>::id).isEmpty()
            prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
            prop("specification").isSameAs(blockSpecification)
        }
    }
}
