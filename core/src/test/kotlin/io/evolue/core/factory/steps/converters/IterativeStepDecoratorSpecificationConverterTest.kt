package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.StepSpecification
import io.evolue.core.factory.steps.IterativeStepDecorator
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.WithMockk
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@WithMockk
internal class IterativeStepDecoratorSpecificationConverterTest {

    @RelaxedMockK
    lateinit var scenarioSpecification: MutableScenarioSpecification

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
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(IterativeStepDecorator::class)
            prop("iterations").isEqualTo(123L)
            prop("delay").isEqualTo(Duration.ZERO)
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
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(IterativeStepDecorator::class)
            prop("iterations").isEqualTo(123L)
            prop("delay").isEqualTo(Duration.ZERO)
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
