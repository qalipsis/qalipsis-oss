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

/**
 * Tracks the statistical distribution of observations recorded for a given campaign.
 *
 * @author Francisca Eze
 */
interface DistributionSummary : Meter<DistributionSummary> {

    /**
     * The configured percentiles to generate in snapshots.
     */
    val percentiles: Collection<Double>

    /**
     * Updates the statistics kept by the summary with the specified amount.
     *
     * @param amount amount for an event being measured. If the amount is less than 0 the value will be dropped.
     */
    fun record(amount: Double)

    /**
     * Returns the number of times that record has been called since this meter was
     * created.
     */
    fun count(): Long

    /**
     * Returns the total amount of all recorded events.
     */
    fun totalAmount(): Double

    /**
     * Returns the distribution average for all recorded events.
     */
    fun mean(): Double

    /**
     * Returns the maximum observation recorded for a single event.
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