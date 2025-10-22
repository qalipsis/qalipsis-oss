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
/**
 * Enum representing different metric values that can be evaluated.
 *
 * @author Francisca Eze
 */
enum class MetricValue(val valueName: String) {
    COUNT("count"),
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