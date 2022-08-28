/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
