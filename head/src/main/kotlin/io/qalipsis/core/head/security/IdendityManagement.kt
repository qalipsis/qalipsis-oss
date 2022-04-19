package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity

interface IdendityManagement {
    suspend fun get(username: String): UserEntity

    suspend fun save(user: UserEntity): UserEntity

    suspend fun delete(username: String)

    suspend fun update(user: UserEntity)
}