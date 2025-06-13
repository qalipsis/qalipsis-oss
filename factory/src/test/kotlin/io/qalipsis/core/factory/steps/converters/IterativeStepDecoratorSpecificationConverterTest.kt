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
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factory.steps.IterativeStepDecorator
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
    internal fun `should decorate step with iterations and zero period`() = runBlockingTest {
        // given
        every { stepSpecification.iterations } returns 123
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(IterativeStepDecorator::class).all {
            prop("iterations").isEqualTo(123L)
            prop("delayMillis").isEqualTo(1L)
            prop("decorated").isSameInstanceAs(decoratedStep)
        }
    }

    @Test
    internal fun `should decorate step with iterations and negative period`() = runBlockingTest {
        // given
        every { stepSpecification.iterations } returns 123
        every { stepSpecification.iterationPeriods } returns Duration.ofMillis(-12)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(IterativeStepDecorator::class).all {
            prop("iterations").isEqualTo(123L)
            prop("delayMillis").isEqualTo(1L)
            prop("decorated").isSameInstanceAs(decoratedStep)
        }
    }

    @Test
    internal fun `should not decorate step without iterations`() = runBlockingTest {
        // given
        every { stepSpecification.iterations } returns 0
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep).isSameInstanceAs(decoratedStep)
    }


    @Test
    internal fun `should not decorate step with negative iterations`() = runBlockingTest {
        // given
        every { stepSpecification.iterations } returns -1
        every { stepSpecification.iterationPeriods } returns Duration.ZERO
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        decorator.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep).isSameInstanceAs(decoratedStep)
    }

}
