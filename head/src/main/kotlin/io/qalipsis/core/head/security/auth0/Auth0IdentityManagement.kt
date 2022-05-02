package io.qalipsis.core.head.security.auth0

import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.Permission
import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.AbstractBufferedEventsPublisher.Companion.log
import io.qalipsis.core.head.security.IdentityManagement
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
    override suspend fun get(identityReference: String): UserIdentity {
        val authUser = getManagementAPI().users().get(identityReference, null).execute()
        val userRoles = getUserRoles(identityReference)
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
    override suspend fun save(user: UserIdentity): UserIdentity {
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
            checkIfRolesExist(user.userRoles)
            assignUserRoles(userWithId.id, user.userRoles.map { it.name }.toList())
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
    override suspend fun update(identityReference: String, user: UserIdentity) {
        val authUser = User()
        authUser.username = user.username
        authUser.name = user.name
        getManagementAPI().users().update(identityReference, authUser).execute()
        val userWithNewMail = User()
        userWithNewMail.email = user.email
        getManagementAPI().users().update(identityReference, userWithNewMail).execute()
        if (user.userRoles.isNotEmpty()) {
            updateUserRoles(identityReference, user.userRoles)
        }
    }

    @Throws(Auth0Exception::class)
    override suspend fun delete(identityReference: String) {
        try {
            removeUserRoles(identityReference, getUserRoles(identityReference).map { it.name }.toMutableList())
        } catch (e: IllegalArgumentException) {
            log.info("Deleted user with id ${identityReference} hasn't any roles")
        } finally {
            getManagementAPI().users().delete(identityReference).execute()
        }
    }

    @Throws(Auth0Exception::class)
    private suspend fun getUsers(): MutableList<User> {
        val users = getManagementAPI().users().list(null).execute()
        return users.items
    }

    private suspend fun updateUserRoles(identityReference: String, userRoles: List<QalipsisRole>) {
        val currentUserRoles = getUserRoles(identityReference).map { it.name }.toMutableList()
        val userRoleNames = userRoles.associate { it.name to it }.toMutableMap()
        if (!currentUserRoles.containsAll(userRoleNames.keys)) {
            currentUserRoles.forEach { userRoleNames.remove(it) }
            checkIfRolesExist(userRoleNames.values.toList())
            assignUserRoles(identityReference, userRoleNames.keys.toList())
        }
        if (!userRoles.map { it.name }.toList().containsAll(currentUserRoles)) {
            currentUserRoles.removeAll(userRoles.map { it.name }.toList())
            removeUserRoles(identityReference, currentUserRoles)
        }
    }

    @Throws(Auth0Exception::class)
    private suspend fun getUserRoles(identityReference: String): MutableList<QalipsisRole> {
        val authUserRoles = getManagementAPI().users().listRoles(identityReference, null).execute()
        return authUserRoles.items.map { QalipsisRole(it.name, it.description) }.toMutableList()
    }

    @Throws(Auth0Exception::class)
    private suspend fun removeUserRoles(identityReference: String, roleNames: MutableList<String>) {
        val authRoles = getRoles()
        val roleIds = authRoles.filter { roleNames.contains(it.name) }.map { it.id }.toList()
        validateIfNotLastUserForRole("billing-admin", authRoles, roleNames)
        validateIfNotLastUserForRole("tenant-admin", authRoles, roleNames)
        getManagementAPI().users().removeRoles(identityReference, roleIds).execute()
    }

    private suspend fun validateIfNotLastUserForRole(
        roleName: String,
        authRoles: MutableList<Role>,
        roleNames: MutableList<String>
    ) {
        if (roleNames.contains(roleName)) {
            val roleId = authRoles.filter { it.name.contains(roleName) }.map { it.id }.first()
            val roleUsers = getManagementAPI().roles().listUsers(roleId, null).execute().items
            if (roleUsers.size < 2) throw ValidationException("Last user for role ${roleName} can't be deleted!")
        }
    }

    @Throws(Auth0Exception::class)
    private suspend fun assignUserRoles(identityReference: String, roleNames: List<String>) {
        val roleIds = getRoles().filter { roleNames.contains(it.name) }.map { it.id }.toList()
        getManagementAPI().users().addRoles(identityReference, roleIds).execute()
    }

    @Throws(Auth0Exception::class)
    private suspend fun getRoles(): MutableList<Role> {
        val roles = getManagementAPI().roles().list(null).execute()
        return roles.items
    }

    @Throws(Auth0Exception::class)
    private suspend fun createRole(name: String, description: String): Role {
        val role = Role()
        role.name = name
        role.description = description
        return getManagementAPI().roles().create(role).execute()
    }

    @Throws(Auth0Exception::class)
    private suspend fun assignRolePermissions(roleId: String, name: String, description: String) {
        val permission = Permission()
        permission.name = name
        permission.description = description
        getManagementAPI().roles().addPermissions(roleId, mutableListOf(permission)).execute()
    }

    private suspend fun checkIfRolesExist(userRoles: List<QalipsisRole>) {
        val userRoleNames = userRoles.associate { it.name to it }.toMutableMap()
        val roles = getRoles().map { it.name }
        if (!roles.containsAll(userRoleNames.keys)) {
            roles.forEach { userRoleNames.remove(it) }
            userRoleNames.forEach {
                val newRole = createRole(it.value.name, it.value.description)
                if (it.value.permissions.isNotEmpty()) {
                    it.value.permissions.forEach {
                        assignRolePermissions(newRole.id, it.name, it.description.orEmpty())
                    }
                }
            }
        }
    }
}
