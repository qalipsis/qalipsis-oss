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
