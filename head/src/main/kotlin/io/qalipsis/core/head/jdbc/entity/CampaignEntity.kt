package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
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
@MappedEntity("campaign", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class CampaignEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val tenantId: Long,
    @field:NotBlank
    @field:Size(min = 5, max = 300)
    val name: String,
    @field:Positive
    @field:Max(999)
    val speedFactor: Double,
    val start: Instant,
    val end: Instant?,
    val result: ExecutionStatus?
) : Entity {

    constructor(
        tenantId: Long = -1,
        campaignName: String,
        speedFactor: Double = 1.0,
        start: Instant = Instant.now(),
        end: Instant? = null,
        result: ExecutionStatus? = null
    ) : this(
        -1,
        Instant.EPOCH, tenantId,
        campaignName, speedFactor, start, end, result
    )
}