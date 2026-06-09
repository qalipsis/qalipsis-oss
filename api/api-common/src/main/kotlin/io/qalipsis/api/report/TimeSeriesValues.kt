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
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Aggregation output for a single data series over a campaign execution.
 *
 * For meter data series, [values] contains the raw step-scope measurements and [summary] holds the
 * single campaign-scope aggregate. For event data series, [values] contains the time-bucketed
 * aggregation points and [summary] is null.
 *
 * @property values time-ordered aggregation results for the duration of the campaign
 * @property summary optional single result representing the overall campaign aggregate
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Time-series values",
    description = "Aggregation output for a single data series over a campaign execution"
)
data class TimeSeriesValues(
    @field:Schema(description = "Time-ordered aggregation results for the duration of the campaign")
    val values: List<TimeSeriesAggregationResult>,

    @field:Schema(description = "Single result representing the overall campaign aggregate, present only for meter data series")
    val summary: TimeSeriesAggregationResult? = null
)
