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

import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.steps.expectations.MeterExpectationSpecification
import io.qalipsis.api.meters.steps.expectations.RateExpectationSpec

/**
 * Implementation of [RateExpectationSpec] to extract the current value property and evaluate it against
 * a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */

class RateExpectationSpecImpl : RateExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<Rate, Double>>()

    override val current: MeterExpectationSpecification<Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.RATE), Rate::current)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }
}