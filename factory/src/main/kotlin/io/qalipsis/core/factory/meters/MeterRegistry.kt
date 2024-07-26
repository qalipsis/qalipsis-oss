/*
 * Copyright 2024 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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