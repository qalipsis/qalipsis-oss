package io.qalipsis.core.head.persistence.repository

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.persistence.entity.FactoryEntity

/**
 * Micronaut data async repository to operate with factory entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface FactoryRepository : CoroutineCrudRepository<FactoryEntity, Long> {

    @Join(value = "selectors", type = Join.Type.LEFT_FETCH)
    suspend fun findByNodeId(factoryId: String): List<FactoryEntity>
}