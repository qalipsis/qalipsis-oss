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
