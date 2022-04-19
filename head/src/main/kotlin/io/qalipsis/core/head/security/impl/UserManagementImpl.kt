package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdendityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import java.time.Instant

class UserManagementImpl(
    private val idendityManagement: IdendityManagement,
    private val userRepository: UserRepository
) : UserManagement {

    override suspend fun get(username: String): UserEntity? {
        val userEntity = userRepository.findByUsername(username)
        if (userEntity != null && userEntity.disabled == null) {
            return userEntity
        }
        return null
    }

    override suspend fun save(userPatches: Collection<UserPatch>, user: UserEntity) {
        userPatches.forEach {
            if (it.apply(user)) {
                idendityManagement.update(user)
                userRepository.update(user)
            }
        }
    }

    override suspend fun delete(username: String) {
        val userEntity = userRepository.findByUsername(username)
        if (userEntity != null) {
            val disabledUser = userEntity.copy(disabled = Instant.now())
            userRepository.update(disabledUser)
            idendityManagement.update(disabledUser)
        }
    }
}