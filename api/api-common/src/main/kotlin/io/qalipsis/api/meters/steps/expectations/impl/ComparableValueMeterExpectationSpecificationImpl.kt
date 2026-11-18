/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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