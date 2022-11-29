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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity

/**
 * Micronaut data repository to operate with [CampaignFactoryEntity].
 *
 * @author Eric Jessé
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface CampaignFactoryRepository : CoroutineCrudRepository<CampaignFactoryEntity, Long> {

    /**
     * Marks the factory as discarded in the campaign, in order to make it available for other campaigns.
     */
    @Query("UPDATE campaign_factory SET discarded = TRUE, version = NOW() WHERE campaign_id in (:campaignId) AND factory_id = :factoryId")
    suspend fun discard(campaignId: Long, factoryId: Collection<Long>): Int
}