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
            WHERE name = :campaignName AND "end" IS NULL AND EXISTS 
            (SELECT * FROM tenant WHERE reference IN (:tenant) AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByNameAndEndIsNull(tenant: Collection<String>, campaignName: String): Long

    suspend fun findIdByName(campaignName: String): Long

    @Query(
        """SELECT campaign.id
            FROM campaign
            WHERE name = :campaignName AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByName(tenant: String, campaignName: String): Long

    /**
     * Marks the open campaign with the specified name [campaignName] as complete with the provided [result].
     */
    @Query("""UPDATE campaign SET version = NOW(), "end" = NOW(), result = :result WHERE name = :campaignName AND "end" IS NULL""")
    suspend fun close(campaignName: String, result: ExecutionStatus): Int
}