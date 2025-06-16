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

package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Module to manage global meter statistics.
 *
 * @author Francisca Eze
 */
interface MeterRegistry {

    /**
     * Creates a new [Counter] metric to be added to the registry. This metric measures the
     * count of specific events collected over time.
     *
     * @param meterId the ID of the counter metric
     */
    fun counter(meterId: Meter.Id): Counter

    /**
     * Creates a new [Timer] metric to be added to the registry. This metric measures the duration of an operation or a task.
     *
     * @param meterId the ID of the timer metric
     * @param percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to an empty set
     */
    fun timer(meterId: Meter.Id, percentiles: Set<Double> = emptySet()): Timer

    /**
     * Creates a new [Gauge] metric to be added to the registry. This metric tracks instantaneous values
     * change over time.
     *
     * @param meterId the ID of the gauge metric
     */
    fun gauge(meterId: Meter.Id): Gauge

    /**
     * Creates a new [DistributionSummary] metric to be added to the registry. This metric
     * provides statistical data about the values observed/collected from an operation.
     *
     * @param meterId the ID of the summary metric
     * @param percentiles a list of values within the range of 1.0-100.0, representing specific points of observation, defaults to an empty set
     */
    fun summary(meterId: Meter.Id, percentiles: Set<Double> = emptySet()): DistributionSummary

    /**
     * Creates the set of instantaneous snapshots for each meter.
     */
    suspend fun snapshots(instant: Instant): Collection<MeterSnapshot>

    /**
     * Creates a new [Rate] metric to be added to the registry. This metric calculates the
     * ratio between two independently tracked measurements.
     *
     * @param meterId the ID of the rate metric
     */
    fun rate(
        meterId: Meter.Id
    ): Rate

    /**
     * Creates a new [Throughput] metric to be added to the registry. This metric
     * tracks the number of hits measured per a configured unit of time, default to seconds.
     *
     * @param meterId the ID of the throughput metric
     */
    fun throughput(
        meterId: Meter.Id,
        unit: ChronoUnit,
        percentiles: Set<Double> = emptySet()
    ): Throughput

    /**
     * Creates the set of cumulative snapshots for each meter.
     */
    suspend fun summarize(instant: Instant): Collection<MeterSnapshot>
}