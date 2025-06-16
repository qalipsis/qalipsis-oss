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

package io.qalipsis.api.report

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Representation of an event to report.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Campaign meter",
    title = "Campaign time-based meter",
    description = "Meter generated during the execution of a campaign, either for monitoring or execution report"
)
data class TimeSeriesMeter(
    override val name: String,
    override val timestamp: Instant,
    @get:Schema(description = "Technical type of the meter")
    val type: String,
    override val tags: Map<String, String>? = null,
    override val campaign: String? = null,
    override val scenario: String? = null,

    @get:Schema(description = "Count of calls")
    val count: Long? = null,

    @get:Schema(description = "Current value of a gauge")
    val value: BigDecimal? = null,

    @get:Schema(description = "Sum of the values of a summary meter")
    val sum: BigDecimal? = null,
    @get:Schema(description = "Mean of the values of a summary meter")
    val mean: BigDecimal? = null,
    @get:Schema(description = "Max of the values of a summary meter")
    val max: BigDecimal? = null,

    @get:Schema(description = "Count of active long tasks")
    val activeTasks: Int? = null,
    @get:Schema(description = "Cumulative duration of the long tasks")
    val duration: Duration? = null,

    @get:Schema(description = "Sum of the execution durations of timers")
    val sumDuration: Duration? = null,
    @get:Schema(description = "Mean of the execution durations of timers")
    val meanDuration: Duration? = null,
    @get:Schema(description = "Max of the execution durations of timers")
    val maxDuration: Duration? = null,

    @get:Schema(description = "Additional measurement values of the meter")
    val other: Map<String, Number>? = null
) : TimeSeriesRecord