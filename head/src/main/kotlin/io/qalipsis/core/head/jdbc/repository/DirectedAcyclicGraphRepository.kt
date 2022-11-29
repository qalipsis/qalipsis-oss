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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import kotlinx.coroutines.flow.Flow

/**
 * Micronaut data async repository to operate with directed_acyclic_graph entity
 *
 * @author rklymenko
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface DirectedAcyclicGraphRepository : CoroutineCrudRepository<DirectedAcyclicGraphEntity, Long> {

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    override fun findAll(): Flow<DirectedAcyclicGraphEntity>

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    override suspend fun findById(id: Long): DirectedAcyclicGraphEntity?

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    suspend fun findByScenarioIdIn(scenarioIds: Iterable<Long>): Collection<DirectedAcyclicGraphEntity>

    suspend fun deleteByScenarioIdIn(scenarioIds: Iterable<Long>): Int
}