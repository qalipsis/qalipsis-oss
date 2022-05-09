package io.qalipsis.core.head.security.auth0

import com.auth0.json.mgmt.users.User
import io.qalipsis.core.head.security.RoleName
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a user
 */
internal interface Auth0Patch {
    /**
     * Applies a change on the [User] and returns true if and only if the change was actually
     * performed on [user].
     */
    suspend fun apply(user: User): Boolean
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing name property of a user
 */
internal class NameAuth0Patch(
    @field:NotBlank
    @field:Size(min = 1, max = 150)
    private val newDisplayName: String
) : Auth0Patch {
    override suspend fun apply(user: User): Boolean {
        return if (user.name != newDisplayName.trim()) {
            user.name = newDisplayName.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing email property of a user
 */
internal class EmailAuth0Patch(
    @field:NotBlank
    @field:Email
    private val newEmailAddress: String
) : Auth0Patch {
    override suspend fun apply(user: User): Boolean {
        return if (user.email != newEmailAddress.trim()) {
            user.email = newEmailAddress.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing username property of a user
 */
internal class UsernameAuth0Patch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newUsername: String
) : Auth0Patch {

    override suspend fun apply(user: User): Boolean {
        return if (user.username != newUsername.trim()) {
            user.username = newUsername.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing roles property of a user
 */
internal class AddRoleAuth0Patch(
    private val tenant: String,
    private val rolesToAssign: Collection<RoleName>,
    private val operations: Auth0Operations
) : Auth0Patch {

    override suspend fun apply(user: User): Boolean {
        val actualRolesToAssign = rolesToAssign + RoleName.TENANT_USER
        val auth0RolesIds = operations.listRolesIds(tenant, actualRolesToAssign, true)
        operations.assignRoles(user.id, auth0RolesIds)
        // False is always returned because the change is directly done and does not affect the instance user.
        return false
    }
}

internal class RemoveRoleAuth0Patch(
    private val tenant: String,
    private val rolesToRemove: Collection<RoleName>,
    private val operations: Auth0Operations
) : Auth0Patch {

    override suspend fun apply(user: User): Boolean {
        // The roles to check are restricted to the ones to remove.
        val userRoles = operations.getUserRolesInTenant(user.id, tenant)
            .map { it.asRoleName(tenant) }.intersect(rolesToRemove.toSet())
        operations.validateAdministrationRolesRemoval(tenant, userRoles)
        val auth0RolesIds = operations.listRolesIds(tenant, userRoles, false)
        operations.unassignRoles(user.id, auth0RolesIds)
        // False is always returned because the change is directly done and does not affect the instance user.
        return false
    }
}