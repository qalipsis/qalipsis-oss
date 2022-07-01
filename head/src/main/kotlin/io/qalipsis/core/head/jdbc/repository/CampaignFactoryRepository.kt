package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity

/**
 * Micronaut data repository to operate with [CampaignFactoryEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.VOLATILE])
internal interface CampaignFactoryRepository : CoroutineCrudRepository<CampaignFactoryEntity, Long> {

    /**
     * Marks the factory as discarded in the campaign, in order to make it available for other campaigns.
     */
    @Query("UPDATE campaign_factory SET discarded = TRUE, version = NOW() WHERE campaign_id in (:campaignId) AND factory_id = :factoryId")
    suspend fun discard(campaignId: Long, factoryId: Collection<Long>): Int
}