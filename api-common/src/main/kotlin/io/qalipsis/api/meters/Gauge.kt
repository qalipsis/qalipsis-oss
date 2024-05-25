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
