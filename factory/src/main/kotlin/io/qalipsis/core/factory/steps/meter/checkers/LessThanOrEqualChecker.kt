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
 * Asserts the value is less than or equal to the expected threshold, using the "less than" and "equals" operators.
 *
 * @param threshold the reference value used in comparison
 *
 * @author Francisca Eze
 */
class LessThanOrEqualChecker<T : Comparable<T>>(private val threshold: T) : ValueChecker<T> {

    override fun check(value: T): MeterAssertionViolation? {
        return supplyUnless(value <= threshold) {
            MeterAssertionViolation("Value should be less than or equal to $threshold")
        }
    }
}