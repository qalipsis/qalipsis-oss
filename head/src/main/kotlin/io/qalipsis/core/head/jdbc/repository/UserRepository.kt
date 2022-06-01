package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.UserEntity

/**
 * Micronaut data repository to operate with [UserEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserRepository : CoroutineCrudRepository<UserEntity, Long> {

    suspend fun findByUsername(username: String): UserEntity

    suspend fun findIdByUsername(username: String): Long

    suspend fun findUsernameById(id: Long): String

}