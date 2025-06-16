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

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Meter to track a large number of short running events.
 *
 * @author Francisca Eze
 */
interface Timer : Meter<Timer> {

    /**
     * The configured percentiles to generate in snapshots.
     */
    val percentiles: Collection<Double>

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount Duration of a single event being measured by this timer.
     * @param unit Time unit for the amount being recorded.
     */
    fun record(amount: Long, unit: TimeUnit?)

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param duration Duration of a single event being measured by this timer.
     */
    fun record(duration: Duration) {
        record(duration.toNanos(), TimeUnit.NANOSECONDS)
    }

    /**
     * Executes the supplier block and records the time taken.
     *
     * @param block function to be executed and execution time measured.
     * @param <T> The return type of the [Supplier].
     */
    suspend fun <T> record(block: suspend () -> T): T

    /**
     * Returns the number of times that stop has been called on this timer.
     */
    fun count(): Long

    /**
     * Calculates the total time of recorded events.
     *
     * @param unit The base unit of time to scale the total to.
     */
    fun totalTime(unit: TimeUnit?): Double

    /**
     * Calculates the average for all recorded duration.
     *
     * @param unit The base unit of time to scale the mean to.
     */
    fun mean(unit: TimeUnit?): Double

    /**
     * The maximum time recorded for of a single event.
     *
     * @param unit The base unit of time to scale the max to.
     */
    fun max(unit: TimeUnit?): Double

    /**
     * Specifies a percentile in the domain. It expresses the point where an observation falls within a
     * given range of other observations.
     *
     * @param percentile the percentage point to be observed
     */
    fun percentile(percentile: Double, unit: TimeUnit?): Double
}