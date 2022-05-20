package io.qalipsis.core.head.jdbc.repository

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Version
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.qalipsis.core.head.jdbc.entity.UserEntity
import java.time.Instant

/**
 * Micronaut data repository to operate with [UserEntity].
 *
 * @author Palina Bril
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserRepository : CoroutineCrudRepository<UserEntity, Long> {

    suspend fun findByUsername(username: String): UserEntity

    suspend fun findIdByUsername(username: String): Long

    suspend fun findByIdentityIdIn(identitiesIds: Collection<String>): Collection<UserEntity>

    suspend fun findUsernameByIdentityId(identityId: String): String

    /**
     * Update the identity reference of a QALIPSIS User.
     *
     * @param id the ID of the QALIPSIS User
     * @param version the current version of the QALIPSIS User
     * @param identityReference the identity reference to set on the QALIPSIS User
     */
    suspend fun updateIdentityId(@Id id: Long, @Version version: Instant, identityId: String?): Int

}