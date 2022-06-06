package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity

/**
 * Micronaut data repository to operate with [CampaignEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface CampaignRepository : CoroutineCrudRepository<CampaignEntity, Long> {

    @Query(
        """SELECT campaign.id
            FROM campaign
            WHERE key = :campaignKey AND "end" IS NULL AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByKeyAndEndIsNull(tenant: String, campaignKey: String): Long

    @Query(
        """SELECT *
            FROM campaign
            WHERE key = :campaignKey AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findByKey(tenant: String, campaignKey: String): CampaignEntity

    @Query(
        """SELECT campaign.id
            FROM campaign
            WHERE key = :campaignKey AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByKey(tenant: String, campaignKey: String): Long

    /**
     * Marks the open campaign with the specified name [campaignKey] as complete with the provided [result].
     */
    @Query(
        """UPDATE campaign SET version = NOW(), "end" = NOW(), result = :result WHERE key = :campaignKey AND "end" IS NULL 
        AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Int
}