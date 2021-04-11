package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factories.steps.ReportingStepDecorator
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
internal class ReportingStepDecoratorSpecificationConverterTest {

    @RelaxedMockK
    lateinit var scenarioSpecification: StepSpecificationRegistry

    @RelaxedMockK
    lateinit var directedAcyclicGraph: DirectedAcyclicGraph

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, String>

    @RelaxedMockK
    lateinit var campaignStateKeeper: CampaignStateKeeper

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    @InjectMockKs
    lateinit var decorator: ReportingStepDecoratorSpecificationConverter

    @Test
    internal fun `should have order 100`() {
        // then + when
        assertEquals(100, decorator.order)
    }

    @Test
    internal fun `should decorate step when the reporting is expected`() = runBlockingTest {
        // given
        every { stepSpecification.reporting.reportErrors } returns true
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(ReportingStepDecorator::class).all {
            prop("campaignStateKeeper").isSameAs(campaignStateKeeper)
            prop("decorated").isSameAs(decoratedStep)
        }
    }

    @Test
    internal fun `should decorate step when no reporting is expected`() = runBlockingTest {
        // given
        every { stepSpecification.reporting.reportErrors } returns false
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep).isSameAs(decoratedStep)
    }

}
