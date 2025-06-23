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
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.steps.DistributionSummaryMeterStepSpecificationImpl
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.meter.DistributionSummaryMeterStep
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanOrEqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanChecker
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
internal class DistributionSummaryMeterStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<DistributionSummaryMeterStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<DistributionSummaryMeterStepSpecificationImpl<*>>()))
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
        val spec = DistributionSummaryMeterStepSpecificationImpl("test-throughput", block)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<DistributionSummaryMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(DistributionSummaryMeterStep::class).all {
            prop(DistributionSummaryMeterStep<*>::name).isEqualTo("my-step")
            prop("meterName").isEqualTo("test-throughput")
            prop("block").isEqualTo(block)
            prop("percentiles").isEqualTo(emptySet<Double>())
            typedProp<List<Pair<DistributionSummary.() -> Double, ValueChecker<Double>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        val spec = DistributionSummaryMeterStepSpecificationImpl("test-throughput", block)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<DistributionSummaryMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(DistributionSummaryMeterStep::class).all {
            prop(DistributionSummaryMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-throughput")
            prop("block").isEqualTo(block)
            prop("percentiles").isEqualTo(emptySet<Double>())
            typedProp<List<Pair<DistributionSummary.() -> Double, ValueChecker<Double>>>>("checkers").isEmpty()
        }
    }

    @Test
    internal fun `should convert spec with failure conditions`() = runBlocking {
        // given
        val block: (context: StepContext<Unit, Unit>, input: Unit) -> Double =
            { _, _ -> 12.0 }
        val spec = DistributionSummaryMeterStepSpecificationImpl("test-throughput", block)
            .shouldFailWhen {
                max.isGreaterThan(12.0)
                mean.isLessThan(14.0)
                percentile(45.0).isGreaterThanOrEqual(299.9)
            }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Unit>(creationContext as StepCreationContext<DistributionSummaryMeterStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(DistributionSummaryMeterStep::class).all {
            prop(DistributionSummaryMeterStep<*>::name).isEmpty()
            prop("meterName").isEqualTo("test-throughput")
            prop("block").isEqualTo(block)
            typedProp<Collection<Double>>("percentiles").all {
                hasSize(1)
                containsOnly(45.0)
            }
            typedProp<List<Pair<DistributionSummary.() -> Double, ValueChecker<Double>>>>("checkers").all {
                hasSize(3)
                index(0).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            io.mockk.every { max() } returns 344.912
                        })
                    }.isEqualTo(344.912)
                    prop("checker") { it.second }.isInstanceOf(GreaterThanChecker::class).prop("threshold")
                        .isEqualTo(12.0)
                }
                index(1).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            io.mockk.every { mean() } returns 123.32
                        })
                    }.isEqualTo(123.32)
                    prop("checker") { it.second }.isInstanceOf(LessThanChecker::class).prop("threshold")
                        .isEqualTo(14.0)
                }
                index(2).all {
                    prop("value extractor") { it.first }.transform {
                        it(mockk {
                            io.mockk.every { percentile(45.0) } returns 1654.2342
                        })
                    }.isEqualTo(1654.2342)
                    prop("checker") { it.second }.isInstanceOf(GreaterThanOrEqualChecker::class).prop("threshold")
                        .isEqualTo(299.9)
                }
            }
        }
    }

}
