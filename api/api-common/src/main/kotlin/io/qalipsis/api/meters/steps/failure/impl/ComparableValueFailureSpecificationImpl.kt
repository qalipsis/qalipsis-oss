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
import io.qalipsis.api.meters.steps.checkSpecification.BetweenValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.EqualValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.GreaterThanOrEqualValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.GreaterThanValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.LessThanOrEqualValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.LessThanValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.NotBetweenValueSpecification
import io.qalipsis.api.meters.steps.checkSpecification.ValueCheckSpecification

/**
 * Implementation of the [ComparableValueFailureSpecification].
 *
 * @author Francisca Eze
 */
class ComparableValueFailureSpecificationImpl<M : Meter<M>, T : Comparable<T>>(
    override val valueExtractor: M.() -> T,
) : ComparableValueFailureSpecification<M, T> {

    override var checkSpec: ValueCheckSpecification<T>? = null

    override fun isGreaterThan(threshold: T) {
        checkSpec = GreaterThanValueSpecification(threshold)
    }

    override fun isLessThan(threshold: T) {
        checkSpec = LessThanValueSpecification(threshold)
    }

    override fun isBetween(lowerBound: T, upperBound: T) {
        checkSpec = BetweenValueSpecification(lowerBound, upperBound)
    }

    override fun isNotBetween(lowerBound: T, upperBound: T) {
        checkSpec = NotBetweenValueSpecification(lowerBound, upperBound)
    }

    override fun isEqual(threshold: T) {
        checkSpec = EqualValueSpecification(threshold)
    }

    override fun isGreaterThanOrEqual(threshold: T) {
        checkSpec = GreaterThanOrEqualValueSpecification(threshold)
    }

    override fun isLessThanOrEqual(threshold: T) {
        checkSpec = LessThanOrEqualValueSpecification(threshold)
    }

}
