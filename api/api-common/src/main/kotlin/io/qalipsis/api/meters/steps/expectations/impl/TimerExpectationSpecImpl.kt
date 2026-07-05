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