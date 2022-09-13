package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity

/**
 * Micronaut data repository to operate with [CampaignScenarioEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface CampaignScenarioRepository : CoroutineCrudRepository<CampaignScenarioEntity, Long> {

    suspend fun findByCampaignId(campaignId: Long): Collection<CampaignScenarioEntity>

    /**
     * Marks the not yet started scenario with the specified name [scenarioName] of campaign [campaignKey] as started.
     */
    @Query(
        """UPDATE campaign_scenario SET version = NOW(), "start" = NOW() WHERE name = :scenarioName AND "start" IS NULL 
        AND EXISTS (SELECT * FROM campaign WHERE key = :campaignKey AND campaign_scenario.campaign_id = campaign.id 
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id))"""
    )
    suspend fun start(tenant: String, campaignKey: String, scenarioName: ScenarioName): Int

    /**
     * Marks the scenario with the specified name [scenarioName] of campaign [campaignKey] as complete with the provided [result].
     */
    @Query(
        """UPDATE campaign_scenario SET version = NOW(), "end" = NOW() WHERE name = :scenarioName AND "end" IS NULL 
        AND EXISTS (SELECT * FROM campaign WHERE key = :campaignKey AND campaign_scenario.campaign_id = campaign.id 
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id))"""
    )
    suspend fun complete(tenant: String, campaignKey: String, scenarioName: ScenarioName): Int

    @Query(
        """SELECT distinct campaign_scenario_entity_.name 
            FROM campaign_scenario as campaign_scenario_entity_ 
            WHERE campaign_scenario_entity_.name ILIKE any (array[:namePatterns]) 
            AND EXISTS (SELECT 1 FROM campaign WHERE campaign_scenario_entity_.campaign_id = id AND campaign.key IN (:campaignKeys)
                AND campaign.tenant_id = :tenantId)"""
    )
    suspend fun findNameByNamePatternsAndCampaignKeys(
        tenantId: Long,
        namePatterns: Collection<String>,
        campaignKeys: Collection<String>
    ): List<String>

    @Query(
        """SELECT distinct campaign_scenario_entity_.name 
            FROM campaign_scenario as campaign_scenario_entity_ 
            WHERE EXISTS (SELECT 1 FROM campaign WHERE campaign_scenario_entity_.campaign_id = id AND campaign.key IN (:campaignKeys)
                AND campaign.tenant_id = :tenantId)"""
    )
    suspend fun findNameByCampaignKeys(tenantId: Long, campaignKeys: Collection<String>): List<String>

}