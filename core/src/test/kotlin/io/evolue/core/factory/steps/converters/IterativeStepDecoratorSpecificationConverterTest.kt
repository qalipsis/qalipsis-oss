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
import io.evolue.core.factory.steps.IterativeStepDecorator
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class IterativeStepDecoratorSpecificationConverterTest {

    @Test
    internal fun `should have order 750`() {
        // given
        val converter = IterativeStepDecoratorSpecificationConverter()

        // then + when
        assertEquals(750, converter.order)
    }

    @Test
    internal fun `should decorate step with iterations and zero period`() {
        // given
        val converter = IterativeStepDecoratorSpecificationConverter()
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { iterations } returns 123
                every { iterationPeriods } returns Duration.ZERO
            })

        // when
        val result = runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>, mockedCreatedStep)
        }

        // then
        assertThat(result).all {
            isInstanceOf(IterativeStepDecorator::class)
            prop("iterations").isEqualTo(123L)
            prop("delay").isEqualTo(Duration.ZERO)
            prop("decorated").isSameAs(mockedCreatedStep)
        }
    }

    @Test
    internal fun `should decorate step with iterations and negative period`() {
        // given
        val converter = IterativeStepDecoratorSpecificationConverter()
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { iterations } returns 123
                every { iterationPeriods } returns Duration.ofMillis(-12)
            })

        // when
        val result = runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>, mockedCreatedStep)
        }

        // then
        assertThat(result).all {
            isInstanceOf(IterativeStepDecorator::class)
            prop("iterations").isEqualTo(123L)
            prop("delay").isEqualTo(Duration.ZERO)
            prop("decorated").isSameAs(mockedCreatedStep)
        }
    }

    @Test
    internal fun `should not decorate step without iterations`() {
        // given
        val converter = IterativeStepDecoratorSpecificationConverter()
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { iterations } returns 0
                every { iterationPeriods } returns Duration.ZERO
            })

        // when
        val result = runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>, mockedCreatedStep)
        }

        // then
        assertThat(result).isSameAs(mockedCreatedStep)
    }


    @Test
    internal fun `should not decorate step with negative iterations`() {
        // given
        val converter = IterativeStepDecoratorSpecificationConverter()
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>> {
                every { iterations } returns -1
                every { iterationPeriods } returns Duration.ZERO
            })

        // when
        val result = runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>, mockedCreatedStep)
        }

        // then
        assertThat(result).isSameAs(mockedCreatedStep)
    }

}