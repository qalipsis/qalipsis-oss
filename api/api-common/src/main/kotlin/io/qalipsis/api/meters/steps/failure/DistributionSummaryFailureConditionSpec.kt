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

package io.qalipsis.api.meters.steps.failure

/**
 * Defines the failure condition specification for summary-based meters. It defines how the
 * properties of a [DistributionSummary] should be evaluated against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
interface DistributionSummaryFailureConditionSpec {

    /**
     * Allows evaluation of failure conditions on a percentile property.
     */
    fun percentile(percentile: Double): FailureSpecification<Double>

    /**
     * Allows evaluation of failure conditions on the max property.
     */
    val max: FailureSpecification<Double>

    /**
     * Allows evaluation of failure conditions on the average property.
     */
    val mean: FailureSpecification<Double>
}