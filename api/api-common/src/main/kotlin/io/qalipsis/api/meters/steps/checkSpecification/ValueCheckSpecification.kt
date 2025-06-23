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

package io.qalipsis.api.meters.steps.checkSpecification

/**
 * Represents a specification for checking a comparable value using a condition.
 *
 * @author Francisca Eze
 */
sealed interface ValueCheckSpecification<T>

/**
 * Custom specification that allows for comparison of a value against a threshold.
 */
sealed interface SingleThresholdValueCheckSpecification<T> : ValueCheckSpecification<T> {
    val expected: T
}

/**
 * Custom specification that allows for comparison of a value against a range of values.
 */
sealed interface RangeValueCheckSpecification<T> : ValueCheckSpecification<T> {
    val lowerBound: T
    val upperBound: T
}