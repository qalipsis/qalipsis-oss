package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
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
    @field:NotBlank
    @field:Size(min = 5, max = 300)
    val name: String,
    @field:Positive
    @field:Max(999)
    val speedFactor: Double,
    val start: Instant,
    val end: Instant?,
    val result: ExecutionStatus?,
    @field:Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "campaignId")
    val campaignReports: List<CampaignReportEntity>?
) : Entity {

    constructor(
        campaignId: String,
        speedFactor: Double = 1.0,
        start: Instant = Instant.now(),
        end: Instant? = null,
        result: ExecutionStatus? = null,
        campaignReports: List<CampaignReportEntity>? = null
    ) : this(
        -1,
        Instant.EPOCH,
        campaignId, speedFactor, start, end, result, campaignReports
    )
}