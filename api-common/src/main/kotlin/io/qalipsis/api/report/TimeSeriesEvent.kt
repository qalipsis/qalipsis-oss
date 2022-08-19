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
