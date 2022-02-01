package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphSelectorEntity

/**
 * Micronaut data async repository to operate with directed_acyclic_graph_selector entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface DirectedAcyclicGraphSelectorRepository :
    CoroutineCrudRepository<DirectedAcyclicGraphSelectorEntity, Long> {

    suspend fun deleteByDirectedAcyclicGraphIdIn(dagIds: Iterable<Long>): Int
}