/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.meters.steps.expectations

/**
 * Defines a structured way to evaluate failure conditions through comparisons functions.
 *
 * @author Francisca Eze
 */
interface MeterExpectationSpecification<T : Comparable<T>> {
    /**
     * Asserts the value extracted from the meter is greater than the specified threshold.
     *
     * @param threshold the minimum allowable value; the meter's value must be greater than this.
     */
    fun isGreaterThan(threshold: T)

    /**
     * Asserts the value extracted from the meter is less than the specified threshold.
     *
     * @param threshold the maximal allowable value; the meter's value must be less than this.
     */
    fun isLessThan(threshold: T)

    /**
     * Asserts that the value extracted from the meter is between the specified lower and upper bounds.
     *
     * @param lowerBound the minimum value, not included in the range the meter must exceed.
     * @param upperBound the maximum value, not included in the range the meter must exceed.
     */
    fun isBetween(lowerBound: T, upperBound: T)

    /**
     * Asserts the value extracted from the meter is not between the specified lower and upper bounds.
     *
     * @param lowerBound the minimum value, not included in the range the meter must exceed.
     * @param upperBound the maximum value, not included in the range the meter must exceed.
     */
    fun isNotBetween(lowerBound: T, upperBound: T)

    /**
     * Asserts the value extracted from the meter is equal to the specified threshold.
     *
     * @param threshold the minimum allowable value; the meter's value must be equal to this.
     */
    fun isEqual(threshold: T)

    /**
     * Asserts the value extracted from the meter is greater than or equal to the specified threshold.
     *
     * @param threshold the minimum allowable value; the meter's value must be greater than or equal to this.
     */
    fun isGreaterThanOrEqual(threshold: T)

    /**
     * Asserts the value extracted from the meter is less than or equal to the specified threshold.
     *
     * @param threshold the maximal allowable value; the meter's value must be less than or equal to this.
     */
    fun isLessThanOrEqual(threshold: T)
}
