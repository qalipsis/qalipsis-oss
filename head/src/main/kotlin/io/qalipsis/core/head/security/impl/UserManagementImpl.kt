package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import java.time.Instant

/**
 * There is a default implementation of [UserManagement] interface that encapsulate
 * the “identity management” enabled by configuration
 *
 * @author Palina Bril
 */
class UserManagementImpl(
    private val identityManagement: IdentityManagement,
    private val userRepository: UserRepository
) : UserManagement {

    override suspend fun get(username: String): UserEntity? {
        val userEntity = userRepository.findByUsername(username)
        return if (userEntity?.disabled == null) {
            userEntity
        } else {
            null
        }
    }

    override suspend fun save(user: UserEntity, userPatches: Collection<UserPatch>) {
        if (userPatches.asSequence().map { it.apply(user) }.any()) {
            identityManagement.update(user)
            userRepository.update(user)
        }
    }

    override suspend fun delete(username: String) {
        val userEntity = userRepository.findByUsername(username)
        if (userEntity != null) {
            val disabledUser = userEntity.copy(disabled = Instant.now())
            userRepository.update(disabledUser)
            identityManagement.update(disabledUser)
        }
    }

    override suspend fun create(user: UserEntity) {
        val userWithIdendityReference = identityManagement.save(user)
        userRepository.save(userWithIdendityReference)
    }
}