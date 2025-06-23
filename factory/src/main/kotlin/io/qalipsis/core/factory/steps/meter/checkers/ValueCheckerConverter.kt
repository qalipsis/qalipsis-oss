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
 * Converts a value check specification into an executable checker.
 *
 * @author Francisca Eze
 */
class ValueCheckerConverter {

    /**
     * @param checkSpec contains the comparison rule.
     */
    fun <T : Comparable<T>, M : Meter<M>> convert(
        checkSpec: ValueCheckSpecification<T>
    ): ValueChecker<T> {
        return when (checkSpec) {
            is LessThanValueSpecification -> LessThanChecker(checkSpec.expected)
            is GreaterThanValueSpecification -> GreaterThanChecker(checkSpec.expected)
            is EqualValueSpecification -> EqualsChecker(checkSpec.expected)
            is BetweenValueSpecification -> BetweenChecker(checkSpec.lowerBound, checkSpec.upperBound)
            is NotBetweenValueSpecification -> NotBetweenChecker(checkSpec.lowerBound, checkSpec.upperBound)
            is LessThanOrEqualValueSpecification -> LessThanOrEqualChecker(checkSpec.expected)
            is GreaterThanOrEqualValueSpecification -> GreaterThanOrEqualChecker(checkSpec.expected)
        }
    }
}