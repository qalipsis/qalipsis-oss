package io.qalipsis.core.head.persistence.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.persistence.entity.FactorySelectorEntity

/**
 * Micronaut data async repository to operate with factory_selector entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface FactorySelectorRepository : CoroutineCrudRepository<FactorySelectorEntity, Long>