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

package io.qalipsis.api.meters.steps.expectations.impl

import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.steps.expectations.MeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.ThroughputExpectationSpec

/**
 * Implementation of [ThroughputExpectationSpec] to extract one or more properties of a [Throughput]
 * and evaluate them against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class ThroughputExpectationSpecImpl(val percentiles: MutableSet<Double>) : ThroughputExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<Throughput, Double>>()

    override val max: ComparableValueMeterExpectationSpecification<Throughput, Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("max", Throughput::max)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val mean: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("mean", Throughput::mean)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val current: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("current throughput", Throughput::current)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override fun percentile(percentile: Double): MeterExpectationSpecification<Double> {
        val valueExtractor: Throughput.() -> Double = { percentile(percentile) }
        percentiles.add(percentile)
        val comparableValueFailureSpecification =
            ComparableValueMeterExpectationSpecificationImpl("percentile($percentile)", valueExtractor)
        checks.add(comparableValueFailureSpecification)
        return comparableValueFailureSpecification
    }
}