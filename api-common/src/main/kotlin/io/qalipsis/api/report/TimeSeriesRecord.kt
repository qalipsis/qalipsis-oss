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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Unique interface of time-series record to be returned for reporting purpose.
 *
 * @author Eric Jessé
 */
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = TimeSeriesEvent::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = TimeSeriesEvent::class),
    JsonSubTypes.Type(value = TimeSeriesMeter::class)
)
@Schema(
    title = "Record of time-series data",
    allOf = [
        TimeSeriesEvent::class,
        TimeSeriesMeter::class
    ]
)
interface TimeSeriesRecord {
    @get:Schema(description = "Name of the record")
    val name: String

    @get:Schema(description = "Instant when the record was generated")
    val timestamp: Instant

    @get:Schema(description = "Campaign that generated the record, if any")
    val campaign: String?

    @get:Schema(description = "Scenario that generated the record, if any")
    val scenario: String?

    @get:Schema(description = "Set of tags to better identify the record")
    val tags: Map<String, String>?
}