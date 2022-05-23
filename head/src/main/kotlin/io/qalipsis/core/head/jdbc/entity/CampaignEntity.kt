package io.qalipsis.core.head.jdbc.entity

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Details of a campaign.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Campaign",
    title = "Campaign of QALIPSIS",
    description = "Details of the campaign as persisted into QALIPSIS"
)
@MappedEntity("campaign", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class CampaignEntity(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Schema(description = "Last change of the campaign metadata in QALIPSIS")
    @field:Version
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
) : Entity {

    constructor(
        tenantId: Long = -1,
        campaignName: String,
        speedFactor: Double = 1.0,
        start: Instant = Instant.now(),
        end: Instant? = null,
        result: ExecutionStatus? = null,
        configurer: Long
    ) : this(
        -1,
        Instant.EPOCH, tenantId,
        campaignName, speedFactor, start, end, result, configurer
    )
}