package io.qalipsis.core.head.security

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import jakarta.inject.Singleton

/**
 * Default implementation of [UserManagement] interface that encapsulates
 * the “identity management” enabled by configuration.
 *
 * @author Palina Bril
 */
@Singleton
@Requires(beans = [IdentityManagement::class])
internal class UserManagementImpl(
    private val identityManagement: IdentityManagement,
    private val userRepository: UserRepository
) : UserManagement {

    override suspend fun get(tenant: String, username: String): User? {
        val userEntity = userRepository.findByUsername(username)
        return userEntity.identityId?.let { identityManagement.get(tenant, it) }
            ?.let { User(tenant, userEntity, it) }
    }

    override suspend fun create(tenant: String, user: User): User {
        val createdUser = userRepository.save(UserEntity(username = user.username, displayName = user.displayName))
        val identityToCreate = UserIdentity(
            username = user.username,
            email = user.email,
            displayName = user.displayName,
            emailVerified = user.emailVerified,
            roles = user.roles.toMutableSet()
        )
        val identity = identityManagement.create(tenant, createdUser, identityToCreate)
        return User(tenant, createdUser, identity)
    }

    override suspend fun update(tenant: String, user: User, userPatches: Collection<UserPatch>): User {
        val userEntity = userRepository.findByUsername(user.username)
        val userWasUpdated = userPatches.map { it.apply(userEntity) }.any { it }
        val identity = identityManagement.update(tenant, userEntity, userPatches)

        val updatedUser = if (userWasUpdated) {
            userRepository.update(userEntity)
        } else {
            userEntity
        }
        return User(tenant, updatedUser, identity)
    }

    override suspend fun delete(tenant: String, username: String) {
        val userEntity = userRepository.findByUsername(username)
        identityManagement.delete(tenant, userEntity)
    }

    override suspend fun getAssignableRoles(tenant: String, currentUser: User): Set<RoleName> {
        return currentUser.roles.flatMap { it.assignableRoles }.toSet()
    }

}