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

package io.qalipsis.api.meters.steps.failure.impl

import io.qalipsis.api.meters.Timer
import io.qalipsis.api.meters.steps.failure.FailureSpecification
import io.qalipsis.api.meters.steps.failure.TimerFailureConditionSpec
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [TimerFailureConditionSpec] to extract one or more properties of a [Timer]
 * and evaluate them against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class TimerFailureConditionSpecImpl(val percentiles: MutableSet<Double>) : TimerFailureConditionSpec {

    val checks = mutableListOf<ComparableValueFailureSpecification<Timer, Duration>>()

    override val max: FailureSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(max(TimeUnit.MICROSECONDS).toLong()) }
            val comparableValueFailureSpecification = ComparableValueFailureSpecificationImpl(valueExtractor)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val mean: FailureSpecification<Duration>
        get() {
            val valueExtractor: Timer.() -> Duration = { Duration.ofMillis(mean(TimeUnit.MICROSECONDS).toLong()) }
            val comparableValueFailureSpecification = ComparableValueFailureSpecificationImpl(valueExtractor)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override fun percentile(percentile: Double): FailureSpecification<Duration> {
        val valueExtractor: Timer.() -> Duration =
            { Duration.ofMillis(percentile(percentile, TimeUnit.MICROSECONDS).toLong()) }
        percentiles.add(percentile)
        val comparableValueFailureSpecification = ComparableValueFailureSpecificationImpl(valueExtractor)
        checks.add(comparableValueFailureSpecification)
        return comparableValueFailureSpecification
    }
}