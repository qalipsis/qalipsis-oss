package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
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
@MappedEntity("campaign_scenario", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class CampaignScenarioEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val campaignId: Long,
    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val name: String,
    @field:Positive
    @field:Max(1000000)
    val minionsCount: Int
) : Entity {

    constructor(
        campaignId: Long,
        name: String,
        minionsCount: Int
    ) : this(
        -1,
        Instant.EPOCH,
        campaignId, name, minionsCount
    )
}