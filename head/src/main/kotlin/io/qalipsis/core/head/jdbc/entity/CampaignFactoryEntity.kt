package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant

/**
 * Details of a campaign.
 *
 * @author Eric Jessé
 */
@MappedEntity("campaign_factory", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class CampaignFactoryEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val campaignId: Long,
    val factoryId: Long,
    val discarded: Boolean
) : Entity {

    constructor(
        campaignId: Long,
        factoryId: Long,
        discarded: Boolean = false
    ) : this(-1, Instant.EPOCH, campaignId, factoryId, discarded)
}