package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.StepSpecification
import io.evolue.core.factory.steps.TimeoutStepDecorator
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class TimeoutStepDecoratorSpecificationConverterTest {

    @Test
    internal fun `should have order 500`() {
        // given
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = TimeoutStepDecoratorSpecificationConverter(meterRegistry)

        // then + when
        assertEquals(500, converter.order)
    }

    @Test
    internal fun `should decorate step with timeout`() {
        // given
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = TimeoutStepDecoratorSpecificationConverter(meterRegistry)
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { timeout } returns Duration.ofMillis(123)
            })
        creationContext.createdStep(mockedCreatedStep)

        // when
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(TimeoutStepDecorator::class)
            prop("timeout").isEqualTo(Duration.ofMillis(123))
            prop("decorated").isSameAs(mockedCreatedStep)
            prop("meterRegistry").isSameAs(meterRegistry)
        }
    }

    @Test
    internal fun `should not decorate step without timeout`() {
        // given
        val meterRegistry: MeterRegistry = relaxedMockk();
        val converter = TimeoutStepDecoratorSpecificationConverter(meterRegistry)
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { timeout } returns null
            })
        creationContext.createdStep(mockedCreatedStep)

        // when
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep).isSameAs(mockedCreatedStep)
    }
}