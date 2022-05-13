package io.qalipsis.core.head.security

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
    suspend fun get(tenant: String, username: String): User?

    /**
     *  Applies the different patches to [user] and persists those changes.
     */
    suspend fun update(tenant: String, user: User, userPatches: Collection<UserPatch>): User

    /**
     * Marks the user as disabled.
     */
    suspend fun delete(tenant: String, username: String)

    /**
     * Creates new the user.
     */
    suspend fun create(tenant: String, user: User): User

    /**
     * Receives the roles that a user is authorized to assign to another one.
     */
    suspend fun getAssignableRoles(tenant: String, currentUser: User): Set<RoleName>
}