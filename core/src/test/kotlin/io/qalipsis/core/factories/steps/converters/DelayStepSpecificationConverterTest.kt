package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.steps.DelayStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.DelayStep
import io.qalipsis.core.factories.steps.VerificationStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
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
    internal fun `should convert spec with name to step`() = runBlocking {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(DelayStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("delay").isEqualTo(Duration.ofMillis(123))
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlocking {
        // given
        val spec = DelayStepSpecification<Int>(Duration.ofMillis(123))
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<DelayStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(DelayStep::class).all {
            prop(DelayStep<*>::id).isEmpty()
            prop("delay").isEqualTo(Duration.ofMillis(123))
        }
    }
}
