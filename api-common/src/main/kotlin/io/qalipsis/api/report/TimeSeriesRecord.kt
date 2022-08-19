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