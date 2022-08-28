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