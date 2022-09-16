/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import java.time.Instant

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
        """UPDATE campaign_scenario SET version = NOW(), "start" = :start WHERE name = :scenarioName AND "start" IS NULL 
        AND EXISTS (SELECT * FROM campaign WHERE key = :campaignKey AND campaign_scenario.campaign_id = campaign.id 
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id))"""
    )
    suspend fun start(tenant: String, campaignKey: String, scenarioName: ScenarioName, start: Instant): Int

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