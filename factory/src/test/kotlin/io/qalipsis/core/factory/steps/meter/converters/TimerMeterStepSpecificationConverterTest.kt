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
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.steps.TimerMeterStepSpecificationImpl
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.meter.TimerMeterStep
import io.qalipsis.core.factory.steps.meter.checkers.EqualsChecker
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanOrEqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.ValueChecker
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
@Suppress("UNCHECKED_CAST")
internal class TimerMeterStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<TimerMeterStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<TimerMeterStepSpecificationImpl<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Duration =
            { _, _ -> Duration.ofMillis(1000) }
        val spec = TimerMeterStepSpecificationImpl("test-timer", block)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TimerMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(TimerMeterStep::class).all {
            prop(TimerMeterStep<*>::name).isEqualTo("my-step")
            prop("meterName").isEqualTo("test-timer")
            prop("block").isEqualTo(block)
            prop("percentiles").isEqualTo(emptySet<Double>())
            typedProp<List<Pair<Timer.() -> Duration, ValueChecker<Duration>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Duration =
            { _, _ -> Duration.ofMillis(1000) }
        val spec = TimerMeterStepSpecificationImpl("test-timer", block)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TimerMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(TimerMeterStep::class).all {
            prop(TimerMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-timer")
            prop("block").isEqualTo(block)
            prop("percentiles").isEqualTo(emptySet<Double>())
            typedProp<List<Pair<Timer.() -> Duration, ValueChecker<Duration>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec with failure conditions`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Duration =
            { _, _ -> Duration.ofMillis(1000) }
        val spec = TimerMeterStepSpecificationImpl("test-timer", block).shouldFailWhen {
            max.isGreaterThan(Duration.of(2, ChronoUnit.SECONDS))
            mean.isLessThan(Duration.of(1, ChronoUnit.SECONDS))
            percentile(45.0).isEqual(Duration.ofMillis(300))
            percentile(25.0).isLessThanOrEqual(Duration.ofMillis(1000))
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TimerMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(TimerMeterStep::class).all {
            prop(TimerMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-timer")
            prop("block").isEqualTo(block)
            typedProp<Collection<Double>>("percentiles").all {
                hasSize(2)
                containsExactlyInAnyOrder(45.0, 25.0)
            }
            typedProp<List<Pair<Timer.() -> Duration, ValueChecker<Duration>>>>("checkers").all {
                hasSize(4)
                index(0).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            every { max(TimeUnit.MICROSECONDS) } returns 2000.0
                        })
                    }.isEqualTo(Duration.ofMillis(2000))
                    prop("checker") { it.second }.isInstanceOf(GreaterThanChecker::class).prop("threshold")
                        .isEqualTo(Duration.ofSeconds(2))
                }
                index(1).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            every { mean(TimeUnit.MICROSECONDS) } returns 1000.0
                        })
                    }.isEqualTo(Duration.ofMillis(1000))
                    prop("checker") { it.second }.isInstanceOf(LessThanChecker::class).prop("threshold").isEqualTo(Duration.ofSeconds(1))
                }
                index(2).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            every { percentile(45.0, TimeUnit.MICROSECONDS) } returns 100.0
                        })
                    }.isEqualTo(Duration.ofMillis(100))
                    prop("checker") { it.second }.isInstanceOf(EqualsChecker::class).prop("threshold").isEqualTo(Duration.ofMillis(300))
                }
                index(3).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            every { percentile(25.0, TimeUnit.MICROSECONDS) } returns 500.0
                        })
                    }.isEqualTo(Duration.ofMillis(500))
                    prop("checker") { it.second }.isInstanceOf(LessThanOrEqualChecker::class).prop("threshold").isEqualTo(Duration.ofMillis(1000))
                }

            }
        }
    }

}
