package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.TenantEntity

/**
 * Micronaut data repository to operate with [TenantEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface TenantRepository : CoroutineCrudRepository<TenantEntity, Long>{

    suspend fun findIdByReference(reference: String): Long

    suspend fun findByReference(reference: String): TenantEntity

    suspend fun findReferenceById(id: Long): String
}