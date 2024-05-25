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

/**
 * Tracks the statistical distribution of events.
 *
 * @author Francisca Eze
 */
interface DistributionSummary : Meter<DistributionSummary> {
    /**
     * Updates the statistics kept by the summary with the specified amount.
     *
     * @param amount amount for an event being measured. If the amount is less than 0 the value will be dropped.
     */
    fun record(amount: Double)

    /**
     * Returns the number of times that record has been called since this timer was
     * created.
     */
    fun count(): Long

    /**
     * Returns the total amount of all recorded events.
     */
    fun totalAmount(): Double

    /**
     * Returns the distribution average, rounded to three decimal places for all recorded events.
     */
    fun mean(): Double {
        val count = count()
        val decimalFormat = DecimalFormat("#.###")
        decimalFormat.roundingMode = RoundingMode.CEILING
        return if (count == 0L) 0.0 else decimalFormat.format(totalAmount() / count).toDouble()
    }

    /**
     * Returns the maximum time of a single event.
     */
    fun max(): Double

    /**
     * Specifies a percentile in the domain. It expresses the point where an observation falls within a given
     * range of other observations.
     *
     * @param percentile the percentage point to be observed
     */
    fun percentile(percentile: Double): Double

}