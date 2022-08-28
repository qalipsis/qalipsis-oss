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
    name = "Campaign event",
    title = "Campaign time-based event",
    description = "Event generated during the execution of a campaign"
)
data class TimeSeriesEvent(
    override val name: String,
    @get:Schema(description = "Logging level of the event")
    val level: String,
    override val timestamp: Instant,
    override val campaign: String? = null,
    override val scenario: String? = null,
    override val tags: Map<String, String>? = null,
    @get:Schema(description = "Message of the event")
    val message: String? = null,
    @get:Schema(description = "Stack trace of the error logged by this event")
    val stackTrace: String? = null,
    @get:Schema(description = "Message of the error logged by this event")
    val error: String? = null,
    @get:Schema(description = "Value of the event as an instant, distinct from the timestamp")
    val date: Instant? = null,
    @get:Schema(description = "Value of the event as a boolean")
    val boolean: Boolean? = null,
    @get:Schema(description = "Value of the event as a number")
    val number: BigDecimal? = null,
    @get:Schema(description = "Value of the event as a duration")
    val duration: Duration? = null
) : TimeSeriesRecord
