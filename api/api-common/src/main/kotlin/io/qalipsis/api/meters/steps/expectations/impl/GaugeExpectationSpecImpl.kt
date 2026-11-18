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

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.steps.expectations.GaugeExpectationSpec

/**
 * Implementation of [GaugeExpectationSpec] to extract the current value property of the gauge
 * and evaluate it against a threshold or range-based conditions.
 *
 * @author Francisca Eze
 */
class GaugeExpectationSpecImpl : GaugeExpectationSpec {

    val checks = mutableListOf<ComparableValueMeterExpectationSpecification<Gauge, Double>>()

    override val value: ComparableValueMeterExpectationSpecification<Gauge, Double>
        get() {
            val comparableValueFailureSpecification =
                ComparableValueMeterExpectationSpecificationImpl(Metric(MetricValue.VALUE), Gauge::value)
            checks.add(comparableValueFailureSpecification)
            return comparableValueFailureSpecification
        }
}