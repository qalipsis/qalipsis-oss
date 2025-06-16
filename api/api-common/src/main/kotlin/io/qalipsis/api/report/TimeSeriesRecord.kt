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