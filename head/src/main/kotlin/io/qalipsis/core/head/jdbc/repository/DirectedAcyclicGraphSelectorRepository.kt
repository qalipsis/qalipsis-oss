package io.qalipsis.core.head.jdbc.repository

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphTagEntity

/**
 * Micronaut data async repository to operate with directed_acyclic_graph_selector entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
internal interface DirectedAcyclicGraphSelectorRepository :
    CoroutineCrudRepository<DirectedAcyclicGraphTagEntity, Long> {

    suspend fun deleteByDirectedAcyclicGraphIdIn(dagIds: Collection<Long>): Int
}