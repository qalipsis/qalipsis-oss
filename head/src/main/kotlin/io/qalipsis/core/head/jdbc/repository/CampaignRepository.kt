package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity

/**
 * Micronaut data repository to operate with [CampaignEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
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

    @Query(
        value = """SELECT *
            FROM campaign as campaign_entity_
            LEFT JOIN campaign_scenario s ON campaign_entity_.id = s.campaign_id 
            LEFT JOIN "user" u ON campaign_entity_.configurer = u.id 
            WHERE (campaign_entity_.name ILIKE any (array[:filters]) OR campaign_entity_.key ILIKE any (array[:filters]) OR s.name ILIKE any (array[:filters]) OR u.username ILIKE any (array[:filters]) OR u.display_name ILIKE any (array[:filters]))
            AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        countQuery = """SELECT COUNT(*)
            FROM campaign as campaign_entity_
            LEFT JOIN campaign_scenario s ON campaign_entity_.id = s.campaign_id 
            LEFT JOIN "user" u ON campaign_entity_.configurer = u.id 
            WHERE (campaign_entity_.name ILIKE any (array[:filters]) OR campaign_entity_.key ILIKE any (array[:filters]) OR s.name ILIKE any (array[:filters]) OR u.username ILIKE any (array[:filters]) OR u.display_name ILIKE any (array[:filters]))
            AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        nativeQuery = true
    )
    suspend fun findAll(tenant: String, filters: Collection<String>, pageable: Pageable): Page<CampaignEntity>

    @Query(
        value =
        """SELECT *
            FROM campaign as campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        countQuery = """SELECT COUNT(*)
            FROM campaign campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)""",
        nativeQuery = true
    )
    suspend fun findAll(tenant: String, pageable: Pageable): Page<CampaignEntity>

    @Query(
        """SELECT campaign_entity_.key 
            FROM campaign as campaign_entity_ 
            WHERE EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)
                AND campaign_entity_.key IN (:keys)"""
    )
    suspend fun findKeyByTenantAndKeyIn(tenant: String, keys: Collection<String>): Set<String>

    @Query(
        """SELECT distinct campaign_entity_.key 
            FROM campaign as campaign_entity_ 
            WHERE campaign_entity_.name ILIKE any (array[:namePatterns]) 
            AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)"""
    )
    suspend fun findKeysByTenantAndNamePatterns(tenant: String, namePatterns: Collection<String>): Collection<String>

    @Query(
        """SELECT distinct campaign_entity_.key 
            FROM campaign as campaign_entity_ 
            WHERE campaign_entity_.tenant_id = :tenantId
            AND campaign_entity_.name ILIKE any (array[:namePatterns])"""
    )
    suspend fun findKeysByTenantIdAndNamePatterns(tenantId: Long, namePatterns: Collection<String>): Collection<String>
}