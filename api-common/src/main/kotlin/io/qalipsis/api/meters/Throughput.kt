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

package io.qalipsis.api.meters

import java.time.temporal.ChronoUnit

/**
 * Tracks the number of hits measured per a configured unit of time, default to seconds.
 *
 * @author Francisca Eze
 */
interface Throughput : Meter<Throughput> {

    /**
     * The configured percentiles to generate in snapshots.
     */
    val percentiles: Collection<Double>

    /**
     * The configured time unit of the measurement time, defaults to seconds.
     */
    val unit: ChronoUnit

    /**
     * Returns the most recent measured throughput.
     */
    fun current(): Double {
        return Double.NaN
    }

    /**
     * Returns the maximum throughput observed.
     */
    fun max(): Double

    /**
     * Returns the average throughput observed.
     */
    fun mean(): Double

    /**
     * Specifies a percentile in the domain. It expresses the point where an observation falls within a
     * given range of other observations.
     *
     * @param percentile the percentage point to be observed
     */
    fun percentile(percentile: Double): Double

    /**
     * Returns the overall total of all recorded hits.
     */
    fun total(): Double

    /**
     * Increases the count of hits in the current time frame.
     */
    fun record(amount: Int = 1) = record(amount.toDouble())

    /**
     * Increases the count of hits in the current time frame.
     */
    fun record(amount: Double)
}
