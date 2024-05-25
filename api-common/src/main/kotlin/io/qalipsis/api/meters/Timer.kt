/*
 * Copyright 2023 AERIS IT Solutions GmbH
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

import java.math.RoundingMode
import java.text.DecimalFormat
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
     * Calculates the distribution average, rounded to three decimal places for all recorded events.
     *
     * @param unit The base unit of time to scale the mean to.
     */
    fun mean(unit: TimeUnit?): Double {
        val count = count()
        val decimalFormat = DecimalFormat("#.###")
        decimalFormat.roundingMode = RoundingMode.CEILING
        return if (count == 0L) 0.0 else decimalFormat.format(totalTime(unit) / count).toDouble()
    }

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