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

package io.qalipsis.api.meters.steps.expectations.impl

import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.steps.expectations.CounterExpectationSpec

/**
 * Implementation of [CounterExpectationSpec] to extract the count property and evaluate it against
 * a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class CounterExpectationSpecImpl : CounterExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<Counter, Double>>()

    override val count: ComparableValueMeterExpectationSpecification<Counter, Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("count", Counter::count)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }
}