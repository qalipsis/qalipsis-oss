package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.RoleEntity
import io.qalipsis.core.head.security.RoleName

/**
 * Micronaut data repository to operate with [RoleEntity].
 *
 * @author Eric Jessé
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
internal interface RoleRepository : CoroutineCrudRepository<RoleEntity, Long> {

    suspend fun findByTenantAndNameIn(tenant: String, names: Collection<RoleName>): Set<RoleEntity>

}