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
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.FactoryEntity

/**
 * Micronaut data async repository to operate with factory entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface FactoryRepository : CoroutineCrudRepository<FactoryEntity, Long> {

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    override suspend fun findById(id: Long): FactoryEntity?

    @Query(
        """SELECT factory.*, 
            factory_tag.id as tags_id, factory_tag.factory_id as tags_factory_id, factory_tag.key as tags_key, factory_tag.value as tags_value
            FROM factory LEFT JOIN factory_tag ON factory_id = factory.id WHERE factory.node_id IN (:factoryIds)
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)
            """
    )
    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    suspend fun findByNodeIdIn(tenant: String, factoryIds: Collection<String>): List<FactoryEntity>

    suspend fun findIdByNodeIdIn(factoryIds: Collection<String>): List<Long>

    @Query(
        """SELECT DISTINCT factory.id 
            FROM factory LEFT JOIN factory_tag ON factory_id = factory.id WHERE factory.node_id IN (:factoryIds)
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)
            """
    )
    suspend fun findIdByNodeIdIn(tenant: String, factoryIds: Collection<String>): List<Long>

    /**
     * Searching for the factories currently supporting the expected scenarios, and being healthy in the past 2 minutes.
     */
    @Query(
        """SELECT factory.*, 
            factory_tag.id as tags_id, factory_tag.factory_id as tags_factory_id, factory_tag.key as tags_key, factory_tag.value as tags_value
            FROM factory LEFT JOIN factory_tag ON factory_id = factory.id WHERE
            EXISTS (SELECT * FROM scenario WHERE factory_id = factory.id AND name in (:names) AND enabled = true)
            AND EXISTS -- The factory should be healthy as latest known state within  the last 2 minutes.
                (SELECT * FROM factory_state healthy WHERE factory_id = factory.id AND state = 'HEALTHY' and health_timestamp > (now() - interval '$HEALTH_QUERY_INTERVAL')
                    AND NOT EXISTS (SELECT * FROM factory_state WHERE factory_id = factory.id AND state <> 'HEALTHY' and health_timestamp > healthy.health_timestamp))
            AND NOT EXISTS -- The factory should not be used in a running campaign.
                (SELECT * FROM campaign WHERE "end" IS NULL 
                    AND EXISTS (SELECT * FROM campaign_factory WHERE factory_id = factory.id AND campaign_id = campaign.id AND discarded = false)
                )
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)
                """
    )
    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    suspend fun getAvailableFactoriesForScenarios(tenant: String, names: Collection<String>): List<FactoryEntity>

    private companion object {

        const val HEALTH_QUERY_INTERVAL = "2 minutes"

    }
}