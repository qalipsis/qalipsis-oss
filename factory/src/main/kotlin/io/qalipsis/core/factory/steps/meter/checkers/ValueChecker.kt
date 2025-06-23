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

import io.qalipsis.core.factory.steps.meter.MeterAssertionViolation

/**
 * Base interface that handles comparisons between property values and their expected thresholds.
 *
 * @author Francisca Eze
 */
interface ValueChecker<T> {

    /**
     * Performs a comparison between the value and the specified threshold and
     * returns a violation error message for checks that didn't meet the specified threshold condition.
     *
     * @param value the reference value used to evaluate whether the check passes.
     */
    fun check(value: T): MeterAssertionViolation?
}