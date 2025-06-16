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
import io.qalipsis.api.context.CampaignKey
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Result of the aggregation of time-series data generated during campaign executions.
 *
 * @property start start instant of the aggregated bucket
 * @property elapsed elapsed time between the start of the aggregation and the start of this result
 * @property campaign key of the campaign that generated the data
 * @property value numeric result of the aggregation
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Result of an aggregation of time-series data",
    description = "Single point of result of an aggregation of time-series data generated during campaign executions"
)
data class TimeSeriesAggregationResult(
    @field:Schema(description = "Start of the aggregation bucket")
    val start: Instant,

    @field:Schema(description = "Elapsed time between the start of the aggregation and the start of this result")
    val elapsed: Duration,

    @field:Schema(description = "Key of the campaign that generated the data")
    val campaign: CampaignKey? = null,

    @field:Schema(description = "Numeric result of the aggregation")
    val value: BigDecimal? = null
)
