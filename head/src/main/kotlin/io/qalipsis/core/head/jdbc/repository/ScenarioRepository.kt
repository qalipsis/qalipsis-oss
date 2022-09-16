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
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity

/**
 * Micronaut data async repository to operate with scenario entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface ScenarioRepository : CoroutineCrudRepository<ScenarioEntity, Long> {

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun deleteById(id: Long): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id = :id")
    override suspend fun delete(entity: ScenarioEntity): Int

    @Query("UPDATE scenario SET enabled = FALSE WHERE id IN (:id)")
    override suspend fun deleteAll(entities: Iterable<ScenarioEntity>): Int

    @Query(
        "SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id WHERE name in (:names) AND enabled = true AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)"
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findActiveByName(tenant: String, names: Collection<String>): List<ScenarioEntity>

    @Query(
        "SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id WHERE factory_id = :factoryId AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id)"
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findByFactoryId(tenant: String, factoryId: Long): List<ScenarioEntity>

    @Query(
        """SELECT * FROM scenario LEFT JOIN factory ON factory_id = factory.id 
            WHERE enabled = true 
            AND EXISTS -- The factory should be healthy as latest known state within  the last 2 minutes.
                (SELECT * FROM factory_state fs WHERE fs.factory_id = factory.id AND fs.state = 'HEALTHY' and fs.health_timestamp > (now() - interval '${HEALTH_QUERY_INTERVAL}')
                AND NOT EXISTS (SELECT * FROM factory_state WHERE factory_id = factory.id AND state <> 'HEALTHY' and health_timestamp > fs.health_timestamp))
            AND EXISTS (SELECT * FROM tenant WHERE reference = :tenant AND id = factory.tenant_id) 
            ORDER BY CASE :sort WHEN 'default_minions_count' THEN default_minions_count END, 
            CASE :sort WHEN 'name' THEN scenario.name END, 
            CASE :sort WHEN 'id' THEN scenario.id END 
            """
    )
    @Join(value = "dags", type = Join.Type.LEFT)
    suspend fun findAllActiveWithSorting(tenant: String, sort: String?): List<ScenarioEntity>

    private companion object {

        const val HEALTH_QUERY_INTERVAL = "2 minute"

    }
}