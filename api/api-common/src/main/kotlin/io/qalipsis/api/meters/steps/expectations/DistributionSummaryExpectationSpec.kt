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

package io.qalipsis.api.meters.steps.expectations

/**
 * Defines the expectation specification for summary-based meters. It defines how the
 * properties of a [DistributionSummary] should be evaluated against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
interface DistributionSummaryExpectationSpec {

    /**
     * Allows evaluation of failure conditions on a percentile property.
     */
    fun percentile(percentile: Double): MeterExpectationSpecification<Double>

    /**
     * Allows evaluation of failure conditions on the max property.
     */
    val max: MeterExpectationSpecification<Double>

    /**
     * Allows evaluation of failure conditions on the average property.
     */
    val mean: MeterExpectationSpecification<Double>
}