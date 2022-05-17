package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity

/**
 * Service to proceed with the user data, which is used by the implementation of [UserManagement] to propagate
 * the changes in the identity management platform (Keycloak or Auth0).
 *
 * @author Palina Bril
 */
internal interface IdentityManagement {

    /**
     * Returns a user from the identity management platform .
     */
    suspend fun get(tenant: String, identityReference: String): UserIdentity

    /**
     * Saves the user to the identity management platform.
     */
    suspend fun create(tenant: String, user: UserEntity, identity: UserIdentity): UserIdentity

    /**
     * Delete a user from the identity management platform .
     */
    suspend fun delete(tenant: String, user: UserEntity)

    /**
     * Saves changes to the user into the identity management platform.
     */
    suspend fun update(tenant: String, user: UserEntity, patches: Collection<UserPatch>): UserIdentity

    /**
     * Returns all the users from the identity management platform assigned to the tenant.
     */
    suspend fun listUsers(tenant: String): List<UserIdentity>
}