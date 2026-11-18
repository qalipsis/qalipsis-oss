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

import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.steps.expectations.MeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.TimerExpectationSpec
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [TimerExpectationSpec] to extract one or more properties of a [Timer]
 * and evaluate them against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class TimerExpectationSpecImpl(val percentiles: MutableSet<Double>) : TimerExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<Timer, Duration>>()

    override val max: MeterExpectationSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(max(TimeUnit.MILLISECONDS).toLong()) }
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.MAX), valueExtractor)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val mean: MeterExpectationSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(mean(TimeUnit.MILLISECONDS).toLong()) }
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.MEAN), valueExtractor)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override fun percentile(percentile: Double): MeterExpectationSpecification<Duration> {
        val valueExtractor: Timer.() -> Duration =
            { Duration.ofMillis(percentile(percentile, TimeUnit.MILLISECONDS).toLong()) }
        percentiles.add(percentile)
        val comparableValueFailureSpecification =
            ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.PERCENTILE, percentile), valueExtractor)
        checks.add(comparableValueFailureSpecification)
        return comparableValueFailureSpecification
    }
}