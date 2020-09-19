package io.evolue.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.evolue.api.steps.AssertionStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factories.steps.AssertionStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class AssertionStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<AssertionStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<AssertionStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = AssertionStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<AssertionStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(AssertionStep::class)
            prop("id").isEqualTo("my-step")
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("assertionBlock").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = AssertionStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<AssertionStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(AssertionStep::class)
            prop("id").isNotNull()
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("assertionBlock").isSameAs(blockSpecification)
        }
    }
}
