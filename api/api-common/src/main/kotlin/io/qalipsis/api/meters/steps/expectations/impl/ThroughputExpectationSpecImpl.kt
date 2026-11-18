/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.MAX), Throughput::max)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val mean: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.MEAN), Throughput::mean)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val current: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.THROUGHPUT), Throughput::current)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override fun percentile(percentile: Double): MeterExpectationSpecification<Double> {
        val valueExtractor: Throughput.() -> Double = { percentile(percentile) }
        percentiles.add(percentile)
        val comparableValueFailureSpecification =
            ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.PERCENTILE, percentile), valueExtractor)
        checks.add(comparableValueFailureSpecification)
        return comparableValueFailureSpecification
    }
}