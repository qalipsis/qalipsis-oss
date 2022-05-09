package io.qalipsis.core.head.security.auth0

import com.auth0.json.mgmt.users.User
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.security.UserIdentity
import io.qalipsis.core.head.security.UserPatch
import jakarta.inject.Singleton
import org.apache.commons.lang3.RandomStringUtils

/**
 * Implementation of [IdentityManagement] for [Auth0](https://www.auth0.com).
 *
 * @author Palina Bril
 */
@Singleton
@Requires(beans = [Auth0Configuration::class])
@Replaces(IdentityManagement::class)
internal class Auth0IdentityManagement(
    private val patchConverter: Auth0PatchConverter,
    private val operations: Auth0Operations,
    private val userRepository: UserRepository
) : IdentityManagement {

    override suspend fun get(tenant: String, identityReference: String): UserIdentity {
        val identity = operations.getUser(identityReference)

        return UserIdentity(
            id = identity.id,
            username = identity.username,
            email = identity.email,
            displayName = identity.name,
            emailVerified = identity.isEmailVerified,
            blocked = identity.isBlocked,
            roles = getRolesNamesInTenant(identityReference, tenant).toMutableSet()
        )
    }

    override suspend fun create(tenant: String, user: UserEntity, identity: UserIdentity): UserIdentity {
        val authUser = User()
        authUser.email = identity.email
        authUser.username = identity.username
        authUser.name = identity.displayName
        authUser.isEmailVerified = false
        authUser.isBlocked = identity.blocked
        // A password is mandatory to create a new user. We then add a new complex password and we expect that
        // the user will reset it.
        authUser.setPassword(RandomStringUtils.randomAlphanumeric(20).toCharArray())
        authUser.setVerifyEmail(true)

        val createdIdentity = operations.createUser(authUser)
        userRepository.updateIdentityId(user.id, user.version, createdIdentity.id)

        val rolesToAssign = identity.roles + RoleName.TENANT_USER
        val auth0RolesIds = operations.listRolesIds(tenant, rolesToAssign, true)
        operations.assignRoles(createdIdentity.id, auth0RolesIds)

        return identity.copy(id = createdIdentity.id)
    }

    override suspend fun update(tenant: String, user: UserEntity, patches: Collection<UserPatch>): UserIdentity {
        val identity = operations.getUser(user.identityId!!)
        if (patchConverter.convert(tenant, patches).map { it.apply(identity) }.any { it }) {
            operations.updateUser(identity)
        }

        return UserIdentity(
            id = identity.id,
            username = identity.username,
            email = identity.email,
            displayName = identity.name,
            emailVerified = identity.isEmailVerified,
            blocked = identity.isBlocked ?: false,
            roles = getRolesNamesInTenant(identity.id, tenant).toMutableSet()
        )
    }

    override suspend fun delete(tenant: String, user: UserEntity) {
        val userRoles = getRolesNamesInTenant(user.identityId!!, tenant)
        operations.validateAdministrationRolesRemoval(tenant, userRoles)
        operations.removeFromTenant(user.identityId, tenant)

        // If no more roles are assigned to the user, it can be deleted.
        if (operations.getAllUserRoles(user.identityId).isEmpty()) {
            operations.deleteUser(user.identityId)
            userRepository.updateIdentityId(user.id, user.version, null)
        }
    }

    /**
     * Returns the public / non technical roles of the user.
     */
    private suspend fun getRolesNamesInTenant(
        userId: String,
        tenant: String
    ): Collection<RoleName> = operations.getUserRolesInTenant(userId, tenant)
        .map { it.asRoleName(tenant) }
        .filterNot { it == RoleName.TENANT_USER }

}