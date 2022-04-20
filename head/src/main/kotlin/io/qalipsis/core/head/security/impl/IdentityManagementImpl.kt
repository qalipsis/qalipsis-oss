package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.security.IdentityManagement
import jakarta.inject.Singleton

/**
 * There is a default implementation of [IdentityManagement] interface that encapsulate
 *
 * @author Palina Bril
 */

//FIXME it will be replaced by real implementation later
@Singleton
internal class IdentityManagementImpl : IdentityManagement {

    override suspend fun get(username: String): UserEntity {
        return UserEntity(username = "qalipsis", displayName = "qalipsis")
    }

    override suspend fun save(user: UserEntity): UserEntity {
        return UserEntity(username = "qalipsis", displayName = "qalipsis")
    }

    override suspend fun delete(username: String) {
    }

    override suspend fun update(user: UserEntity) {
    }
}