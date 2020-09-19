package io.evolue.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.evolue.api.steps.DelayStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factories.steps.DelayStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<DelayedStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<DelayStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(DelayStep::class)
            prop("id").isEqualTo("my-step")
            prop("delay").isEqualTo(Duration.ofMillis(123))
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(DelayStep::class)
            prop("id").isNotNull()
            prop("delay").isEqualTo(Duration.ofMillis(123))
        }
    }
}
