package io.qalipsis.core.head.persistence.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.persistence.entity.FactoryStateEntity
import java.time.Instant

/**
 * Micronaut data async repository to operate with factory_state entity
 *
 * @author rklymenko
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface FactoryStateRepository : CoroutineCrudRepository<FactoryStateEntity, Long> {

    suspend fun findTop1ByFactoryIdOrderByVersionDesc(factoryId: Long): List<FactoryStateEntity>

    suspend fun deleteByFactoryIdAndHealthTimestampBefore(factoryId: Long, before: Instant): Int
}