package io.qalipsis.core.factories.steps.topicrelatedsteps

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factories.steps.TimeoutStepDecorator
import io.qalipsis.core.factories.steps.converters.TimeoutStepDecoratorSpecificationConverter
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class TimeoutStepDecoratorSpecificationConverterTest {

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var scenarioSpecification: StepSpecificationRegistry

    @RelaxedMockK
    lateinit var directedAcyclicGraph: DirectedAcyclicGraph

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, String>

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    @InjectMockKs
    lateinit var decorator: TimeoutStepDecoratorSpecificationConverter

    @Test
    internal fun `should have order 500`() {
        // then + when
        assertEquals(500, decorator.order)
    }

    @Test
    internal fun `should decorate step with timeout`() = runBlockingTest {
        // given
        every { stepSpecification.timeout } returns Duration.ofMillis(123)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(TimeoutStepDecorator::class).all {
            prop("timeout").isEqualTo(Duration.ofMillis(123))
            prop("decorated").isSameAs(decoratedStep)
            prop("meterRegistry").isSameAs(meterRegistry)
        }
    }

    @Test
    internal fun `should not decorate step without timeout`() = runBlockingTest {
        // given
        every { stepSpecification.timeout } returns null
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep).isSameAs(decoratedStep)
    }
}
