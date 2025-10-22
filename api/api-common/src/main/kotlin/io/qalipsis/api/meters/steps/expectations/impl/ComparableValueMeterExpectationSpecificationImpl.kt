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
 * Implementation of the [ComparableValueMeterExpectationSpecification].
 *
 * @author Francisca Eze
 */
class ComparableValueMeterExpectationSpecificationImpl<M : Meter<M>, T : Comparable<T>>(
    metric  : Metric,
    override val valueExtractor: M.() -> T,
) : ComparableValueMeterExpectationSpecification<M, T> {

    override var checkSpec: ValueCheckSpecification<T>? = null

    private val metricValue = if (metric.metricValue == MetricValue.PERCENTILE) {
        "${metric.metricValue.valueName}(${metric.percentile})"
    } else {
        metric.metricValue.valueName
    }

    override fun isGreaterThan(threshold: T) {
        checkSpec = GreaterThanValueSpecification(metricValue, threshold)
    }

    override fun isLessThan(threshold: T) {
        checkSpec = LessThanValueSpecification(metricValue, threshold)
    }

    override fun isBetween(lowerBound: T, upperBound: T) {
        checkSpec = BetweenValueSpecification(metricValue, lowerBound, upperBound)
    }

    override fun isNotBetween(lowerBound: T, upperBound: T) {
        checkSpec = NotBetweenValueSpecification(metricValue, lowerBound, upperBound)
    }

    override fun isEqual(threshold: T) {
        checkSpec = EqualValueSpecification(metricValue, threshold)
    }

    override fun isGreaterThanOrEqual(threshold: T) {
        checkSpec = GreaterThanOrEqualValueSpecification(metricValue, threshold)
    }

    override fun isLessThanOrEqual(threshold: T) {
        checkSpec = LessThanOrEqualValueSpecification(metricValue, threshold)
    }

}