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

package io.qalipsis.api.meters.steps.failure.impl

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.steps.checkSpecification.ValueCheckSpecification
import io.qalipsis.api.meters.steps.failure.FailureSpecification

/**
 * Definition of a failure specification that defines how to extract a comparable value from a [Meter]
 * and how to evaluate this value against a given threshold.
 *
 * @author Francisca Eze
 */
interface ComparableValueFailureSpecification<M : Meter<M>, T : Comparable<T>> : FailureSpecification<T> {

    val valueExtractor: M.() -> T

    var checkSpec: ValueCheckSpecification<T>?
}