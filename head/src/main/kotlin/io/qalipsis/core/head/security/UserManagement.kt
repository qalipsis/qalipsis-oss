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
     * Marks the user as disabled for the provided tenant.
     */
    suspend fun disable(tenant: String, username: String)

    /**
     * Creates new the user.
     */
    suspend fun create(tenant: String, user: User): User

    /**
     * Receives the roles that a user is authorized to assign to another one.
     */
    suspend fun getAssignableRoles(tenant: String, currentUser: User): Set<RoleName>

    /**
     * Returns all users related to a tenant.
     */
    suspend fun findAll(tenant: String): List<User>

    /**
     * Retrieves the username from the identity ID received.
     */
    suspend fun getUsernameFromIdentityId(identityId: String): String

}