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
 * Defines the expectation specification for gauge-based meters. It defines
 * how the current value of a [Gauge] should be evaluated against a threshold
 * or range-based conditions.
 *
 * @author Francisca Eze
 */
interface GaugeExpectationSpec {

    /**
     * Allows evaluation of failure conditions on the current value property.
     */
    val value: MeterExpectationSpecification<Double>
}