package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import kotlinx.coroutines.flow.Flow

/**
 * Micronaut data async repository to operate with directed_acyclic_graph entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface DirectedAcyclicGraphRepository : CoroutineCrudRepository<DirectedAcyclicGraphEntity, Long> {

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    override fun findAll(): Flow<DirectedAcyclicGraphEntity>

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    override suspend fun findById(id: Long): DirectedAcyclicGraphEntity?

    @Join(value = "tags", type = Join.Type.LEFT_FETCH)
    suspend fun findByScenarioIdIn(scenarioIds: Iterable<Long>): Collection<DirectedAcyclicGraphEntity>

    suspend fun deleteByScenarioIdIn(scenarioIds: Iterable<Long>): Int
}