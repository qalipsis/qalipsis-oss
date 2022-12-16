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
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import java.time.Duration
import java.time.Instant

/**
 * Micronaut data repository to operate with [CampaignEntity].
 *
 * @author Eric Jessé
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface CampaignRepository : CoroutineCrudRepository<CampaignEntity, Long> {

    suspend fun findIdByKey(campaignKey: String): Long

    suspend fun findByKey(campaignKey: String): CampaignEntity

    @Query(
        """SELECT campaign.id
            FROM campaign
            WHERE key = :campaignKey AND "end" IS NULL AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByTenantAndKeyAndEndIsNull(tenant: String, campaignKey: String): Long?

    @Query(
        """SELECT *
            FROM campaign
            WHERE key = :campaignKey AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findByTenantAndKey(tenant: String, campaignKey: String): CampaignEntity?

    @Query(
        """SELECT campaign.id
            FROM campaign
            WHERE key = :campaignKey AND EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun findIdByTenantAndKey(tenant: String, campaignKey: String): Long?

    /**
     * Marks the not yet started campaign with the specified name [campaignKey] as in preparation.
     */
    @Query(
        """UPDATE campaign SET version = NOW(), result = 'IN_PROGRESS' WHERE key = :campaignKey AND "start" IS NULL 
        AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun prepare(tenant: String, campaignKey: String): Int

    /**
     * Marks the not yet started campaign with the specified name [campaignKey] as started.
     */
    @Query(
        """UPDATE campaign SET version = NOW(), "start" = :start, timeout = :timeout WHERE key = :campaignKey AND "start" IS NULL 
        AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun start(tenant: String, campaignKey: String, start: Instant, timeout: Instant?): Int

    /**
     * Marks the open campaign with the specified name [campaignKey] as complete with the provided [result].
     */
    @Query(
        """UPDATE campaign SET version = NOW(), "end" = NOW(), result = :result WHERE key = :campaignKey AND "end" IS NULL 
        AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign.tenant_id)"""
    )
    suspend fun complete(tenant: String, campaignKey: String, result: ExecutionStatus): Int

    @Query(
        value = """SELECT *
            FROM campaign as campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)
                AND (
                    campaign_entity_.name ILIKE any (array[:filters]) OR campaign_entity_.key ILIKE any (array[:filters])
                    OR EXISTS (SELECT 1 from campaign_scenario s WHERE campaign_entity_.id = s.campaign_id AND s.name ILIKE any (array[:filters]))
                    OR EXISTS (SELECT 1 from "user" u WHERE campaign_entity_.configurer = u.id AND (u.username ILIKE any (array[:filters]) OR u.display_name ILIKE any (array[:filters])))
                )""",
        countQuery = """SELECT COUNT(*)
            FROM campaign as campaign_entity_
            WHERE EXISTS 
            (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)
                AND (
                    campaign_entity_.name ILIKE any (array[:filters]) OR campaign_entity_.key ILIKE any (array[:filters])
                    OR EXISTS (SELECT 1 from campaign_scenario s WHERE campaign_entity_.id = s.campaign_id AND s.name ILIKE any (array[:filters]))
                    OR EXISTS (SELECT 1 from "user" u WHERE campaign_entity_.configurer = u.id AND (u.username ILIKE any (array[:filters]) OR u.display_name ILIKE any (array[:filters])))
                )""",
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

    @Query(
        value =
        """SELECT 
                MIN("start") as "minStart", 
                MAX(COALESCE("end", NOW())) as "maxEnd", 
                (EXTRACT(EPOCH FROM MAX(AGE(COALESCE("end", NOW()), start))))::int as "maxDurationSec"
            FROM campaign as campaign_entity_
            WHERE key in (:campaignKeys)
                AND EXISTS 
                    (SELECT * FROM tenant WHERE reference = :tenant AND id = campaign_entity_.tenant_id)"""
    )
    suspend fun findInstantsAndDuration(tenant: String, campaignKeys: Collection<String>): CampaignsInstantsAndDuration?

    /**
     * Aggregates campaign results over specified time frame.
     */
    @Query(
        """WITH buckets as (SELECT CAST (start AS TIMESTAMP WITH TIME ZONE), start + :timeframe::interval
           AS endVal FROM generate_series(:startIdentifier::timestamp, :endIdentifier::timestamp - :timeframe::interval,
           :timeframe::interval) AS start)
           SELECT COUNT(*) as count, buckets.start AS "seriesStart", c.result as status
           FROM campaign c RIGHT JOIN buckets ON c.start >= buckets.start AND c.start < buckets.endVal 
            WHERE c.end IS NOT NULL AND EXISTS
                                        (SELECT 1
                                         FROM tenant 
                                         WHERE id = c.tenant_id AND reference = :tenantReference
                                        )
           GROUP BY "seriesStart", c.result ORDER BY "seriesStart", c.result """
    )
    suspend fun retrieveCampaignsStatusHistogram(
        tenantReference: String, startIdentifier: Instant, endIdentifier: Instant, timeframe: Duration
    ): Collection<CampaignResultCount>

    @Introspected
    data class CampaignResultCount(val seriesStart: Instant, val status: ExecutionStatus, val count: Int)

}