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
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.COUNT), Counter::count)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }
}