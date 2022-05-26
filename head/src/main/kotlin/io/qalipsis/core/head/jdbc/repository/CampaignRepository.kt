package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
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
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByNameAndEndIsNull(tenant: String, campaignName: String): Long

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

    @Query(
        """SELECT *
            FROM campaign
            LEFT JOIN campaign_scenario s ON campaign.id = s.campaign_id 
            LEFT JOIN users ON campaign.configurer = users.id 
            WHERE campaign.name LIKE :filter OR s.name LIKE :filter OR users.username LIKE :filter
            AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findAll(tenant: String, filter: String): List<CampaignEntity>

    @Query(
        value =
        """SELECT *
            FROM campaign as campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        countQuery = """SELECT count (*)
            FROM campaign campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        nativeQuery = true
    )
    suspend fun findAll(tenant: String, pageable: Pageable): Page<CampaignEntity>
}