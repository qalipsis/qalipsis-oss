/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factory.steps.ReportingStepDecorator
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
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var reportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    private val stepStartTimeout: Duration = Duration.ofSeconds(543)

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
            prop("reportLiveStateRegistry").isSameAs(reportLiveStateRegistry)
            prop("decorated").isSameAs(decoratedStep)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("stepStartTimeout").isEqualTo(Duration.ofSeconds(543))
            prop("reportErrors").isEqualTo(true)
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
        assertThat(creationContext.createdStep!!).isInstanceOf(ReportingStepDecorator::class).all {
            prop("reportLiveStateRegistry").isSameAs(reportLiveStateRegistry)
            prop("decorated").isSameAs(decoratedStep)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("stepStartTimeout").isEqualTo(Duration.ofSeconds(543))
            prop("reportErrors").isEqualTo(false)
        }
    }

}
