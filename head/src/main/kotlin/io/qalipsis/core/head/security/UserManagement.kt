package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity

/**
 * Service to proceed (get, save, update, delete) the hybrid storage (database and identity management platform)
 * of the user.
 *
 * @author Palina Bril
 */
interface UserManagement {

    /**
     * Returns an enabled user.
     */
    suspend fun get(username: String): UserEntity?

    /**
     * Checks if there are changes for user exists and update the user in both storages if so.
     */
    suspend fun save(user: UserEntity, userPatches: Collection<UserPatch>)

    /**
     * Marks the user as disabled.
     */
    suspend fun delete(username: String)
}