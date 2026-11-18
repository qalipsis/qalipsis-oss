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
/**
 * Enum representing different metric values that can be evaluated.
 *
 * @author Francisca Eze
 */
enum class MetricValue(val valueName: String) {
    COUNT("count"),
    VALUE("value"),
    MEAN("mean"),
    MAX("max"),
    RATE("current rate"),
    THROUGHPUT("current throughput"),
    PERCENTILE("percentile")
}


/**
 * Data class representing a metric with its value type and an optional percentile.
 *
 * @author Francisca Eze
 */
data class Metric(
    val metricValue: MetricValue,
    val percentile: Double? = null
)