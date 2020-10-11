package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factories.steps.IterativeStepDecorator
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class IterativeStepDecoratorSpecificationConverterTest {

    @RelaxedMockK
    lateinit var scenarioSpecification: StepSpecificationRegistry

    @RelaxedMockK
    lateinit var directedAcyclicGraph: DirectedAcyclicGraph

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, String>

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    @InjectMockKs
    lateinit var decorator: IterativeStepDecoratorSpecificationConverter

    @Test
    internal fun `should have order 750`() {
        // then + when
        assertEquals(750, decorator.order)
    }

    @Test
    internal fun `should decorate step with iterations and zero period`() {
        // given
        every { stepSpecification.iterations } returns 123
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        runBlocking {
            decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(IterativeStepDecorator::class).all {
            prop("iterations").isEqualTo(123L)
            prop("delayMillis").isEqualTo(0L)
            prop("decorated").isSameAs(decoratedStep)
        }
    }

    @Test
    internal fun `should decorate step with iterations and negative period`() {
        // given
        every { stepSpecification.iterations } returns 123
        every { stepSpecification.iterationPeriods } returns Duration.ofMillis(-12)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        runBlocking {
            decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(IterativeStepDecorator::class).all {
            prop("iterations").isEqualTo(123L)
            prop("delayMillis").isEqualTo(0L)
            prop("decorated").isSameAs(decoratedStep)
        }
    }

    @Test
    internal fun `should not decorate step without iterations`() {
        // given
        every { stepSpecification.iterations } returns 0
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        runBlocking {
            decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep).isSameAs(decoratedStep)
    }


    @Test
    internal fun `should not decorate step with negative iterations`() {
        // given
        every { stepSpecification.iterations } returns -1
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        runBlocking {
            decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep).isSameAs(decoratedStep)
    }

}
