package io.qalipsis.core.head.security

import io.qalipsis.core.head.security.entity.QalipsisRole
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.RoleName

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
    suspend fun get(tenant: String, username: String): QalipsisUser?

    /**
     *  Applies the different patches to [user] and persists those changes.
     */
    suspend fun save(tenant: String, user: QalipsisUser, userPatches: Collection<UserPatch>): QalipsisUser

    /**
     * Marks the user as disabled.
     */
    suspend fun delete(tenant: String, username: String)

    /**
     * Creates new the user.
     */
    suspend fun create(tenant: String, user: QalipsisUser): QalipsisUser

    /**
     * Receives the roles that a user is authorized to assign to another one.
     */
    suspend fun getAssignableRoles(tenant: String, currentUser: QalipsisUser): Set<RoleName>

    /**
     * Returns all users for the tenant.
     */
    suspend fun getAll(tenant: String): List<QalipsisUser>
}