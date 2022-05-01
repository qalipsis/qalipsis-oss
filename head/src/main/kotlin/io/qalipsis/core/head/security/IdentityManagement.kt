package io.qalipsis.core.head.security

import io.qalipsis.core.head.security.entity.UserIdentity

/**
 * Service to proceed with the user data, which is used by the implementation of [UserManagement] to propagate
 * the changes in the identity management platform (Keycloak or Auth0)
 *
 * @author Palina Bril
 */
interface IdentityManagement {

    /**
     * Returns a user from the identity management platform .
     */
    suspend fun get(identityReference: String): UserIdentity

    /**
     * Saves the user to the identity management platform.
     */
    suspend fun save(user: UserIdentity): UserIdentity

    /**
     * Delete a user from the identity management platform .
     */
    suspend fun delete(identityReference: String)

    /**
     * Saves changes to the user into the identity management platform.
     */
    suspend fun update(identityReference: String, user: UserIdentity)
}