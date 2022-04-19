package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity

interface UserManagement {
    suspend fun get(username: String): UserEntity?

    suspend fun save(userPatches: Collection<UserPatch>, user: UserEntity)

    suspend fun delete(username: String)
}