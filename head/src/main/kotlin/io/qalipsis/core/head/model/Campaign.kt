package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * External representation of a campaign.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Campaign details",
    title = "Details of a running or completed campaign"
)
internal data class Campaign(
    @field:Schema(description = "Last change of the campaign", required = true)
    val version: Instant,

    @field:Schema(description = "Unique identifier of the campaign", required = true)
    val key: String,

    @field:Schema(description = "Display name of the campaign", required = true)
    val name: String,

    @field:Schema(
        description = "Speed factor to apply on the ramp-up strategy, each strategy will apply it differently depending on its own implementation",
        required = true
    )
    val speedFactor: Double,

    @field:Schema(description = "Date and time when the campaign started", required = true)
    val start: Instant?,

    @field:Schema(
        description = "Date and time when the campaign was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Overall execution status of the campaign when completed", required = false)
    val result: ExecutionStatus?,

    @field:Schema(description = "Name of the user, who created the campaign", required = false)
    val configurerName: String?,

    @field:Schema(description = "Scenarios being part of the campaign", required = true)
    val scenarios: Collection<Scenario>
)
