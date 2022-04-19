package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.security.IdendityManagement

class IdendityManagementImpl : IdendityManagement {

    override suspend fun get(username: String): UserEntity {
        return UserEntity(username = "qalipsis", displayName = "qalipsis")
        //TODO("Not yet implemented")
    }

    override suspend fun save(user: UserEntity): UserEntity {
        return UserEntity(username = "qalipsis", displayName = "qalipsis")
        //TODO("Not yet implemented")
    }

    override suspend fun delete(username: String) {
        //TODO("Not yet implemented")
    }

    override suspend fun update(user: UserEntity) {
        //TODO("Not yet implemented")
    }
}