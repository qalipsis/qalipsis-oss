package io.qalipsis.core.head.security.auth0

import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.qalipsis.core.head.security.RoleName

/**
 * Service in charge of the actual interaction with teh Auth0 API to support QALIPSIS use cases.
 */
internal interface Auth0Operations {

    /**
     * Creates a new user at Auth0.
     */
    suspend fun createUser(user: User): User

    /**
     * Retrieves an existing user at Auth0 and throws a failure when a user with the identifier [id] does not exist.
     */
    suspend fun getUser(id: String): User

    /**
     * Updates an existing user at Auth0 and throws a failure when the user does not exist.
     */
    suspend fun updateUser(user: User): User

    /**
     * Deletes an existing user at Auth0.
     */
    suspend fun deleteUser(id: String)

    /**
     * Removes all the granted roles of a tenant from an existing user at Auth0.
     */
    suspend fun removeFromTenant(id: String, tenant: String)

    /**
     * Assigns additional roles to a user at Auth0.
     */
    suspend fun assignRoles(id: String, rolesIds: List<String>)

    /**
     * Removes assigned roles from a user at Auth0.
     */
    suspend fun unassignRoles(id: String, rolesIds: List<String>)

    /**
     * Returns all the roles of an existing user at Auth0.
     */
    suspend fun getAllUserRoles(id: String): Collection<Role>

    /**
     * Returns all the roles of a user in a given tenant.
     */
    suspend fun getUserRolesInTenant(id: String, tenant: String): Collection<Role>

    /**
     * Validates whether removing the administration can be unassign from the user in [tenant].
     */
    suspend fun validateAdministrationRolesRemoval(tenant: String, userRoles: Collection<RoleName>)

    /**
     * Creates new roles in Auth0 if they do not exist and return the mapping between values in [roles] and their IDs at Auth0.
     */
    suspend fun listRolesIds(
        tenant: String,
        roles: Collection<RoleName>,
        createMissingRoles: Boolean = false
    ): List<String>
}