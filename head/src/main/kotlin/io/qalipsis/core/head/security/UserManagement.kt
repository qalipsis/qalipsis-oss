package io.qalipsis.core.head.security

import io.qalipsis.core.head.security.entity.QalipsisUser

/**
 * Service to proceed (get, save, update, delete) the hybrid storage (database and identity management platform)
 * of the user.
 *
 * @author Palina Bril
 */
internal interface UserManagement {

    /**
     * Returns an enabled user.
     */
    suspend fun get(username: String): QalipsisUser?

    /**
     *  Applies the different patches to [user] and persists those changes.
     */
    suspend fun save(user: QalipsisUser, userPatches: Collection<UserPatch>)

    /**
     * Marks the user as disabled.
     */
    suspend fun delete(username: String)

    /**
     * Creates new the user.
     */
    suspend fun create(user: QalipsisUser)
}