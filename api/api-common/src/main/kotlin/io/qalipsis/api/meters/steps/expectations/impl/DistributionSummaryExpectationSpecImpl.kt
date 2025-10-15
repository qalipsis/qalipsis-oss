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

import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.steps.expectations.DistributionSummaryExpectationSpec
import io.qalipsis.api.meters.steps.expectations.MeterExpectationSpecification

/**
 * Implementation of [DistributionSummaryExpectationSpec] to extract one or more properties of a [DistributionSummary]
 * and evaluate them against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class DistributionSummaryExpectationSpecImpl(val percentiles: MutableSet<Double>) :
    DistributionSummaryExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<DistributionSummary, Double>>()

    override val max: ComparableValueMeterExpectationSpecification<DistributionSummary, Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("max", DistributionSummary::max)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override val mean: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl("mean", DistributionSummary::mean)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }

    override fun percentile(percentile: Double): MeterExpectationSpecification<Double> {
        val valueExtractor: DistributionSummary.() -> Double = { percentile(percentile) }
        percentiles.add(percentile)
        val comparableValueFailureSpecification =
            ComparableValueMeterExpectationSpecificationImpl("percentile($percentile)", valueExtractor)
        checks.add(comparableValueFailureSpecification)
        return comparableValueFailureSpecification
    }
}