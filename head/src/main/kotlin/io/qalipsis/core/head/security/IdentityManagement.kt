package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity

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
    suspend fun get(username: String): UserEntity

    /**
     * Saves the user to the identity management platform.
     */
    suspend fun save(user: UserEntity): UserEntity

    /**
     * Delete a user from the identity management platform .
     */
    suspend fun delete(username: String)

    /**
     * Saves changes to the user into the identity management platform.
     */
    suspend fun update(user: UserEntity)
}