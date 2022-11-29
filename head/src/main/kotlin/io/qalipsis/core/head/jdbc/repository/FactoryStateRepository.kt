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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.FactoryStateEntity
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import java.time.Instant

/**
 * Micronaut data async repository to operate with factory_state entity.
 *
 * @author rklymenko
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface FactoryStateRepository : CoroutineCrudRepository<FactoryStateEntity, Long> {

    suspend fun findTop1ByFactoryIdOrderByVersionDesc(factoryId: Long): List<FactoryStateEntity>

    suspend fun deleteByFactoryIdAndHealthTimestampBefore(factoryId: Long, before: Instant): Int

    /**
     * Counts the factories in their current states.
     */
    @Query(
        """
            SELECT COUNT(*) as count, state from (
                SELECT
                    factory_state.factory_id,
                    CASE
                       WHEN factory_state.state IN ('REGISTERED', 'IDLE') AND
                            factory_state.health_timestamp < (now() - interval '1 minute') THEN 'UNHEALTHY'
                       ELSE factory_state.state
                   END as state
                FROM factory_state
                     INNER JOIN (
                        SELECT fs.factory_id factory_id, MAX(fs.health_timestamp) as health_timestamp
                                FROM factory_state fs
                                WHERE EXISTS(
                                              SELECT 1
                                              FROM factory
                                              WHERE fs.factory_id = id
                                                AND EXISTS(
                                                      SELECT 1
                                                      FROM tenant
                                                      where id = factory.tenant_id and reference = :tenantReference
                                                  )
                                          )
                                GROUP BY fs.factory_id) AS last_states ON factory_state.factory_id = last_states.factory_id AND
                                                                       factory_state.health_timestamp =
                                                                       last_states.health_timestamp
                ) as current_states
            GROUP BY state
        """
    )
    suspend fun countCurrentFactoryStatesByTenant(tenantReference: String): Collection<FactoryStateCount>

    @Introspected
    data class FactoryStateCount(val state: FactoryStateValue, val count: Int)
}