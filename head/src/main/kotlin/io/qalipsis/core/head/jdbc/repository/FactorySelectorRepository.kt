package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.FactorySelectorEntity

/**
 * Micronaut data async repository to operate with factory_selector entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface FactorySelectorRepository : CoroutineCrudRepository<FactorySelectorEntity, Long> {

    suspend fun findByFactoryIdIn(factoryIds: Collection<Long>): List<FactorySelectorEntity>

}