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

import io.qalipsis.api.meters.Statistic.COUNT
import io.qalipsis.api.meters.Statistic.MAX
import io.qalipsis.api.meters.Statistic.MEAN
import io.qalipsis.api.meters.Statistic.PERCENTILE
import io.qalipsis.api.meters.Statistic.TOTAL
import io.qalipsis.api.meters.Statistic.TOTAL_TIME
import io.qalipsis.api.meters.Statistic.VALUE


/**
 * Describes the possibilities of values contained in a measurement.
 *
 *  @property TOTAL represents the total of the amount recorded.
 *  @property TOTAL_TIME represents the sum total of time recorded in the base unit time.
 *  @property COUNT represents the rate per second for calls.
 *  @property MAX represents the maximum amount recorded.
 *  @property VALUE instantaneous values at any given time.
 *  @property MEAN represents the average value within a given set of recorded amount.
 *  @property PERCENTILE expresses where an observation falls in a range of other observations.
 *
 *  @author Francisca Eze
 */
enum class Statistic(val value: String) {
    TOTAL("total"),
    TOTAL_TIME("total_time"),
    COUNT("count"),
    MAX("max"),
    VALUE("value"),
    MEAN("mean"),
    PERCENTILE("percentile"),
}