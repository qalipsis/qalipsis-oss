package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.events.EventLogger
import io.evolue.api.steps.AssertionStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.AssertionStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class AssertionStepSpecificationConverterImplTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val eventLogger: EventLogger = relaxedMockk();
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = AssertionStepSpecificationConverter(eventLogger, meterRegistry)

        // when+then
        assertTrue(converter.support(relaxedMockk<AssertionStepSpecification<*, *>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val eventLogger: EventLogger = relaxedMockk();
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = AssertionStepSpecificationConverter(eventLogger, meterRegistry)

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = AssertionStepSpecification(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val eventLogger: EventLogger = relaxedMockk();
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = AssertionStepSpecificationConverter(eventLogger, meterRegistry)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<AssertionStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(AssertionStep::class)
                prop("eventLogger").isSameAs(eventLogger)
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("assertionBlock").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = AssertionStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val eventLogger: EventLogger = relaxedMockk();
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = AssertionStepSpecificationConverter(eventLogger, meterRegistry)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<AssertionStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(AssertionStep::class)
                prop("eventLogger").isSameAs(eventLogger)
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("assertionBlock").isSameAs(blockSpecification)
            }
        }
    }
}