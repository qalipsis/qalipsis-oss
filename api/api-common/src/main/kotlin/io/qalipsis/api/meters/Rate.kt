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
 * Measures the ratio between two independently tracked measurements: a
 * cumulative value and the other being its corresponding benchmark.
 *
 * @author Francisca Eze
 */
interface Rate : Meter<Rate> {

    /**
     * Calculates an instantaneous ratio gotten from dividing the benchmark against its cumulative value.
     */
    fun current(): Double {
        return Double.NaN
    }

    /**
     * Decrease the value of the cumulative measurement by the `amount`.
     *
     * @param amount amount to subtract from the gauge value
     */
    fun decrementTotal(amount: Double = 1.0)

    /**
     * Increase the value of the cumulative measurement by the `amount`.
     *
     * @param amount amount to add to the gauge value
     */
    fun incrementTotal(amount: Double = 1.0)

    /**
     * Decrease the value of the benchmark measurement by the `amount`.
     *
     * @param amount amount to subtract from the gauge value
     */
    fun decrementBenchmark(amount: Double = 1.0)

    /**
     * Increase the value of the cumulative measurement by the `amount`.
     *
     * @param amount amount to add to the gauge value
     */
    fun incrementBenchmark(amount: Double = 1.0)
}
