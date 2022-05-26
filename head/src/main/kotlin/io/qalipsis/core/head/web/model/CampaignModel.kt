package io.qalipsis.core.head.web.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Details of a campaign to return by REST.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Campaign",
    title = "Campaign of QALIPSIS",
    description = "Details of a test campaign, that can still be running or already completed"
)
data class CampaignModel(
    @field:Schema(description = "Unique identifier of the campaign")
    val id: Long,
    @field:Schema(description = "Last change of the campaign metadata in QALIPSIS")
    val version: Instant,
    @field:Schema(description = "Id of the tenant owning the campaign")
    val tenantId: Long,
    @field:Schema(description = "Name of the campaign")
    @field:NotBlank
    @field:Size(min = 5, max = 300)
    val name: String,
    @field:Schema(description = "Speed factor to apply on the campaign")
    @field:Positive
    @field:Max(999)
    val speedFactor: Double,
    @field:Schema(description = "Start timestamp of the campaign in QALIPSIS")
    val start: Instant,
    @field:Schema(description = "End timestamp of the campaign in QALIPSIS", required = false)
    val end: Instant?,
    @field:Schema(description = "Status of the campaign", required = false)
    val result: ExecutionStatus?,
    @field:Schema(description = "Unique identifier of the user who configured the campaign")
    @field:NotBlank
    val configurer: Long
) {
    constructor(entity: CampaignEntity) : this(
        id = entity.id,
        version = entity.version,
        tenantId = entity.tenantId,
        name = entity.name,
        speedFactor = entity.speedFactor,
        start = entity.start,
        end = entity.end,
        result = entity.result,
        configurer = entity.configurer
    )
}