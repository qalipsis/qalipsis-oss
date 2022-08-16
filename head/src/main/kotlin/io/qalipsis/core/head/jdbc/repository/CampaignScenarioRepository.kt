package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
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

    @Query(
        """SELECT distinct campaign_scenario_entity_.name 
            FROM campaign_scenario as campaign_scenario_entity_ 
            WHERE campaign_scenario_entity_.name ILIKE any (array[:namePatterns]) 
            AND EXISTS (SELECT 1 FROM campaign WHERE campaign_scenario_entity_.campaign_id = id AND campaign.key IN (:campaignKeys))"""
    )
    suspend fun findNameByNamePatternsAndCampaignKeys(namePatterns: Collection<String>, campaignKeys: Collection<String>): List<String>

    @Query(
        """SELECT distinct campaign_scenario_entity_.name 
            FROM campaign_scenario as campaign_scenario_entity_ 
            WHERE EXISTS (SELECT 1 FROM campaign WHERE campaign_scenario_entity_.campaign_id = id AND campaign.key IN (:campaignKeys))"""
    )
    suspend fun findNameByCampaignKeys(campaignKeys: Collection<String>): List<String>

}