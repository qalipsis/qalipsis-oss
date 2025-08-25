/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.meter.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.steps.CounterMeterStepSpecificationImpl
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.meter.CounterMeterStep
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.ValueChecker
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
@Suppress("UNCHECKED_CAST")
internal class CounterMeterStepSpecificationImplConverterTest :
    AbstractStepSpecificationConverterTest<CounterMeterStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CounterMeterStepSpecificationImpl<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        val spec = CounterMeterStepSpecificationImpl("test-counter", block)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<CounterMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CounterMeterStep::class).all {
            prop(CounterMeterStep<*>::name).isEqualTo("my-step")
            prop("meterName").isEqualTo("test-counter")
            prop("block").isEqualTo(block)
            typedProp<List<Pair<Counter.() -> Double, ValueChecker<Double>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        val spec = CounterMeterStepSpecificationImpl("test-counter", block)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<CounterMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CounterMeterStep::class).all {
            prop(CounterMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-counter")
            prop("block").isEqualTo(block)
            typedProp<List<Pair<Counter.() -> Double, ValueChecker<Double>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec with failure conditions`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        val spec = CounterMeterStepSpecificationImpl("test-counter", block)
            .shouldFailWhen {
                count.isGreaterThan(9.0)
            }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<CounterMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CounterMeterStep::class).all {
            prop(CounterMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-counter")
            prop("block").isEqualTo(block)
            typedProp<List<Pair<Counter.() -> Double, ValueChecker<Double>>>>("checkers").all {
                hasSize(1)
                index(0).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            every { count() } returns 24.0
                        })
                    }.isEqualTo(24.0)
                    prop("checker") { it.second }.isInstanceOf(GreaterThanChecker::class).prop("threshold")
                        .isEqualTo(9.0)
                }
            }
        }
    }

}
