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
 * Tracks values that go up and down. Publishes an instantaneous sample of the gauge at publishing time.
 *
 * @author Francisca Eze
 */
interface Gauge : Meter<Gauge> {
    /**
     * Triggers sampling of the underlying number or user-defined function that defines the value for the gauge.
     *
     * @return The current value.
     */
    fun value(): Double {
        return Double.NaN
    }

    /**
     * Update the gauge by one.
     */
    fun increment(): Double

    /**
     * Decrease the value of the gauge by one.
     */
    fun decrement(): Double

    /**
     * Decrease the value of the gauge by the amount.
     * @param amount amount to subtract from the gauge value.
     */
    fun decrement(amount: Double): Double

    /**
     * Increase the gauge value by the `amount`.
     * @param amount amount to add to the gauge value.
     */
    fun increment(amount: Double): Double
}
