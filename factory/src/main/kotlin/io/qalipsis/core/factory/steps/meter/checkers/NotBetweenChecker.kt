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

package io.qalipsis.core.factory.steps.meter.checkers

import io.qalipsis.api.lang.supplyUnless
import io.qalipsis.core.factory.steps.meter.MeterAssertionViolation

/**
 * Asserts the value is not between the specified lower and upper bounds.
 *
 * @param lowerBound the minimum value, not included in the range the value must exceed.
 * @param upperBound the maximum value, not included in the range the value must exceed.
 *
 * @author Francisca Eze
 */
class NotBetweenChecker<T : Comparable<T>>(
    private val lowerBound: T,
    private val upperBound: T
) : ValueChecker<T> {

    override fun check(value: T): MeterAssertionViolation? {
        return supplyUnless(isNotBetween(value, lowerBound, upperBound)) {
            MeterAssertionViolation("Value $value should not be between bounds: $lowerBound and $upperBound")
        }
    }

    /**
     * Performs the check to validate that a value is not between the lower and upper bounds.
     */
    private fun isNotBetween(value: T, lowerBound: T, upperBound: T): Boolean {
        return value <= lowerBound || value >= upperBound
    }
}