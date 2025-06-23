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

package io.qalipsis.api.meters.steps.failure

/**
 * Defines a structured way to evaluate failure conditions through comparisons functions.
 *
 * @author Francisca Eze
 */
interface FailureSpecification<T : Comparable<T>> {
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
