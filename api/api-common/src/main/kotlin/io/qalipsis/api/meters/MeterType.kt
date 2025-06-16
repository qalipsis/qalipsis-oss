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

package io.qalipsis.api.meters

import io.qalipsis.api.meters.MeterType.COUNTER
import io.qalipsis.api.meters.MeterType.DISTRIBUTION_SUMMARY
import io.qalipsis.api.meters.MeterType.GAUGE
import io.qalipsis.api.meters.MeterType.RATE
import io.qalipsis.api.meters.MeterType.STATISTICS
import io.qalipsis.api.meters.MeterType.THROUGHPUT
import io.qalipsis.api.meters.MeterType.TIMER


/**
 * Possibilities of [io.qalipsis.api.meters.Meter] that are available.
 *
 * @property COUNTER tracks monotonically increasing values
 * @property GAUGE tracks values that go up and down
 * @property TIMER track a large number of short running events
 * @property DISTRIBUTION_SUMMARY tracks the statistical distribution of events
 * @property STATISTICS tracks the sum total statistical distribution of events across the application
 * @property RATE measures the ratio between two independently tracked measurements
 * @property THROUGHPUT tracks the number of hits measured per a configured unit of time
 * @author Francisca Eze
 */
enum class MeterType(val value: String) {
    COUNTER("counter"),
    GAUGE("gauge"),
    TIMER("timer"),
    DISTRIBUTION_SUMMARY("summary"),
    STATISTICS("statistics"),
    RATE("rate"),
    THROUGHPUT("throughput")
}