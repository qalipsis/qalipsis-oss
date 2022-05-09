package io.qalipsis.core.head.security.auth0

import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.client.mgmt.filter.RolesFilter
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.Permission
import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.micronaut.context.annotation.Requires
import io.micronaut.data.exceptions.EmptyResultException
import io.qalipsis.api.events.AbstractBufferedEventsPublisher.Companion.log
import io.qalipsis.core.head.security.Auth0Patch
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.RoleAuth0Patch
import io.qalipsis.core.head.security.entity.QalipsisPermission
import io.qalipsis.core.head.security.entity.QalipsisRole
import io.qalipsis.core.head.security.entity.UserIdentity
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.xml.bind.ValidationException

/**
 * There is an Auth0 implementation of [IdentityManagement] interface
 *
 * @author Palina Bril
 */
@Requires(property = "identity.manager", value = "auth0")
@Singleton
internal class Auth0IdentityManagement(
    val auth0Properties: Auth0Configuration
) : IdentityManagement {
    /**
     * API for making calls to our application for saving, getting and so on data to/from Auth0
     */
    private var managementAPI: ManagementAPI? = null

    /**
     * API for getting and updating tokens
     */
    private val authAPI = AuthAPI(auth0Properties.domain, auth0Properties.clientId, auth0Properties.clientSecret)

    /**
     * Token details for Auth0
     */
    @Volatile
    private var auth0Token: Auth0Token? = null

    /**
     * Extra time between token expiration and time to make a call.
     * The experation itself is calculated in AuthToken in the field expiresAt
     */
    private val expirationBufferSec: Long = 10
    private val managementApiCreationMutex = Mutex()

    @Throws(Auth0Exception::class)
    suspend fun getManagementAPI(): ManagementAPI {
        val currentTimeEpochSecs = Instant.now().epochSecond
        if (auth0Token == null || auth0Token!!.expiresAt < currentTimeEpochSecs + expirationBufferSec) {
            managementApiCreationMutex.withLock {
                if (auth0Token == null || auth0Token!!.expiresAt < currentTimeEpochSecs + expirationBufferSec) {
                    val tokenHolder = authAPI.requestToken(auth0Properties.apiIdentifier).execute()
                    auth0Token = Auth0Token(tokenHolder.accessToken, tokenHolder.expiresIn)
                    managementAPI = ManagementAPI(auth0Properties.domain, auth0Token!!.accessToken)
                }
            }
        }
        if (managementAPI == null) {
            throw Auth0Exception("The identity management API could not be contacted")
        }
        return managementAPI!!
    }

    @Throws(Auth0Exception::class)
    override suspend fun get(tenant: String, identityReference: String): UserIdentity {
        val authUser = getManagementAPI().users().get(identityReference, null).execute()
        val userRoles = getUserRoles(tenant, identityReference)
        return UserIdentity(
            username = authUser.username,
            email = authUser.email,
            name = authUser.name,
            email_verified = authUser.isEmailVerified,
            user_id = authUser.id,
            userRoles = userRoles
        )
    }

    @Throws(Auth0Exception::class)
    override suspend fun save(tenant: String, user: UserIdentity): UserIdentity {
        val authUser = User()
        authUser.email = user.email
        authUser.username = user.username
        authUser.name = user.name
        authUser.isEmailVerified = user.email_verified
        authUser.setVerifyEmail(user.verify_email)
        authUser.setPassword((user.password).toCharArray())
        authUser.setConnection(user.connection)
        val userWithId = getManagementAPI().users().create(authUser).execute()
        if (user.userRoles.isNotEmpty()) {
            checkIfRolesExist(tenant, user.userRoles)
            assignUserRoles(tenant, userWithId.id, user.userRoles.map { it.name }.toList())
        }
        return UserIdentity(
            username = userWithId.username,
            email = userWithId.email,
            name = userWithId.name,
            email_verified = userWithId.isEmailVerified,
            user_id = userWithId.id,
            userRoles = user.userRoles
        )
    }

    @Throws(Auth0Exception::class)
    override suspend fun update(
        tenant: String, identityReference: String, user: UserIdentity, auth0Patches: List<Auth0Patch>
    ) {
        val authUser = User()
        if (auth0Patches.asSequence().map { it.apply(authUser) }.any()) {
            auth0Patches.forEach { it.apply(authUser) }
            if (authUser.username != null && authUser.email != null) {
                val userTwo = User()
                userTwo.email = user.email
                authUser.email = null
                getManagementAPI().users().update(identityReference, authUser).execute()
                getManagementAPI().users().update(identityReference, userTwo).execute()
            } else {
                getManagementAPI().users().update(identityReference, authUser).execute()
            }
        }
        if (auth0Patches.filterIsInstance<RoleAuth0Patch>().any()) {
            updateUserRoles(tenant, identityReference, user.userRoles)
        }
    }

    @Throws(Auth0Exception::class)
    override suspend fun delete(tenant: String, identityReference: String) {
        try {
            removeUserRoles(
                tenant,
                identityReference,
                getUserRoles(identityReference).map { it.name }.toMutableList()
            )
        } catch (e: IllegalArgumentException) {
            log.info("Deleted user with id $identityReference hasn't any roles")
        } finally {
            getManagementAPI().users().delete(identityReference).execute()
        }
    }

    /**
     * Gets all users from Auth0
     */
    @Throws(Auth0Exception::class)
    override suspend fun getUsers(tenant: String): List<UserIdentity> {
        val users = getManagementAPI().users().list(null).execute()
        return users.items.map {
            try {
                val userRoles = getUserRoles(tenant, it.id)
                UserIdentity(
                    username = it.username,
                    email = it.email,
                    name = it.name,
                    email_verified = it.isEmailVerified,
                    user_id = it.id,
                    userRoles = userRoles
                )
            } catch (e: Auth0Exception){
                null
            }
        }.filterNotNull()
    }

    /**
     * Saves changes in user roles to Auth0
     */
    private suspend fun updateUserRoles(tenant: String, identityReference: String, userRoles: List<QalipsisRole>) {
        val currentUserRoles = getUserRoles(tenant, identityReference).map { it.name }.toMutableSet()
        val userRoleNames = userRoles.associateBy { it.name }
        (userRoleNames.keys - currentUserRoles).takeIf(Collection<String>::isNotEmpty)?.let { newRoles ->
            checkIfRolesExist(tenant, userRoleNames.filterKeys { key -> key in newRoles }.values.toList())
            assignUserRoles(tenant, identityReference, newRoles.toList())
        }
        (currentUserRoles - userRoleNames.keys).takeIf(Collection<String>::isNotEmpty)?.let { obsoleteRoles ->
            removeUserRoles(tenant, identityReference, obsoleteRoles.toMutableList())
        }
    }

    /**
     * Returns all user roles from Auth0 for certain tenant
     */
    @Throws(Auth0Exception::class)
    private suspend fun getUserRoles(tenant: String, identityReference: String): MutableList<QalipsisRole> {
        val authUserRoles =
            getManagementAPI().users().listRoles(identityReference, RolesFilter().withName(tenant)).execute()
        return authUserRoles.items.map { QalipsisRole(it.name, it.description) }.toMutableList()
    }

    /**
     * Returns all user roles from Auth0 for all tenants
     */
    @Throws(Auth0Exception::class)
    private suspend fun getUserRoles(identityReference: String): MutableList<QalipsisRole> {
        val authUserRoles = getManagementAPI().users().listRoles(identityReference, null).execute()
        return authUserRoles.items.map { QalipsisRole(it.name, it.description) }.toMutableList()
    }

    /**
     * Delete user roles in Auth0
     */
    @Throws(Auth0Exception::class)
    private suspend fun removeUserRoles(tenant: String, identityReference: String, roleNames: MutableList<String>) {
        val authRoles = getRoles(tenant)
        val roleIds = authRoles.filter { roleNames.contains(it.name) }.map { it.id }.toList()
        validateIfNotLastUserForRole("$tenant:billing-admin", authRoles, roleNames)
        validateIfNotLastUserForRole("$tenant:tenant-admin", authRoles, roleNames)
        getManagementAPI().users().removeRoles(identityReference, roleIds).execute()
    }

    /**
     * Checks if it is possible to remove role from user. It is not possible in case of last user for the role.
     */
    private suspend fun validateIfNotLastUserForRole(
        roleName: String,
        authRoles: MutableList<Role>,
        roleNames: MutableList<String>
    ) {
        if (roleNames.contains(roleName)) {
            val roleId = authRoles.filter { it.name.contains(roleName) }.map { it.id }.first()
            val roleUsers = getManagementAPI().roles().listUsers(roleId, null).execute().items
            if (roleUsers.size < 2) throw ValidationException("Last user for role $roleName can't be deleted!")
        }
    }

    /**
     * Adds user roles in Auth0
     */
    @Throws(Auth0Exception::class)
    private suspend fun assignUserRoles(tenant: String, identityReference: String, roleNames: List<String>) {
        val roleIds = getRoles(tenant).filter { roleNames.contains(it.name) }.map { it.id }.toList()
        getManagementAPI().users().addRoles(identityReference, roleIds).execute()
    }

    /**
     * Returns all roles exist for certain tenant in Auth0
     */
    @Throws(Auth0Exception::class)
    private suspend fun getRoles(tenant: String): MutableList<Role> {
        val roles = getManagementAPI().roles().list(RolesFilter().withName(tenant)).execute()
        return roles.items
    }

    /**
     * API for getting and updating tokens
     */
    @Throws(Auth0Exception::class)
    private suspend fun createRole(name: String, description: String): Role {
        val role = Role()
        role.name = name
        role.description = description
        return getManagementAPI().roles().create(role).execute()
    }

    /**
     * Saves permissions to roles in Auth0
     */
    @Throws(Auth0Exception::class)
    private suspend fun assignRolePermissions(roleId: String, permissions: List<QalipsisPermission>) {
        getManagementAPI().roles().addPermissions(roleId, permissions.map {
            val permission = Permission()
            permission.name = it.name
            permission.description = it.description
            permission
        }).execute()
    }

    /**
     * Creates new roles in Auth0 if they are not exist
     */
    private suspend fun checkIfRolesExist(tenant: String, userRoles: List<QalipsisRole>) {
        val userRoleNames = userRoles.associateBy { it.name }
        val roles = getRoles(tenant).map { it.name }.toMutableSet()
        (userRoleNames.keys - roles).takeIf(Collection<String>::isNotEmpty)?.let { newRoles ->
            userRoleNames.filterKeys { key -> key in newRoles }.values.forEach {
                val newRole = createRole(it.name, it.description)
                if (it.permissions.isNotEmpty()) {
                    assignRolePermissions(newRole.id, it.permissions)
                }
            }
        }
    }
}