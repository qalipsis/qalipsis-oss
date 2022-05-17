package io.qalipsis.core.head.security.auth0

import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.core.head.jdbc.entity.RoleEntity
import io.qalipsis.core.head.jdbc.repository.RoleRepository
import io.qalipsis.core.head.security.RoleName
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Auth0Operations executes the actual operations onto the [Auth0 API](https://auth0.com/docs/api/management/v2/).
 *
 * @author Eric Jessé
 */
@Singleton
internal class Auth0OperationsImpl(
    private val configuration: Auth0Configuration,
    private val roleRepository: RoleRepository
) : Auth0Operations {

    /**
     * API for making calls to our application for saving, getting and so on data to/from Auth0
     */
    private var managementAPI: ManagementAPI? = null

    /**
     * API for getting and updating tokens
     */
    private val authAPI = AuthAPI(configuration.domain, configuration.clientId, configuration.clientSecret)

    /**
     * Token details for Auth0
     */
    @Volatile
    private var auth0Token: Auth0Token? = null

    private val managementApiCreationMutex = Mutex()

    @Throws(Auth0Exception::class)
    private suspend fun getManagementAPI(): ManagementAPI {
        if (auth0Token?.isValid != true) {
            managementApiCreationMutex.withLock {
                if (auth0Token?.isValid != true) {
                    val tokenHolder =
                        authAPI.requestToken(configuration.apiUrl).executeAsync().asSuspended().get()
                    auth0Token =
                        Auth0Token(tokenHolder.accessToken, TimeUnit.MILLISECONDS.toSeconds(tokenHolder.expiresIn))
                    managementAPI = ManagementAPI(configuration.domain, auth0Token!!.accessToken)
                }
            }
        }
        return managementAPI ?: throw Auth0Exception("The identity management API could not be contacted")
    }

    override suspend fun createUser(user: User): User {
        user.setConnection(configuration.connection)
        return getManagementAPI().users().create(user).executeAsync().asSuspended().get()
    }

    override suspend fun getUser(id: String): User =
        getManagementAPI().users().get(id, null).executeAsync().asSuspended().get()

    override suspend fun updateUser(user: User): User {
        if (user.email.isNotBlank() && user.username.isNotBlank()) {
            // Auth0 does not support to update email and username simultaneously.
            // So the email is first updated, then the other fields.
            val userToUpdate = User()
            userToUpdate.email = user.email
            userToUpdate.setVerifyEmail(true)
            userToUpdate.setConnection(configuration.connection)
            getManagementAPI().users().update(user.id, userToUpdate).executeAsync().asSuspended().get()

            user.email = null
        }

        val userToUpdate = User().apply {
            email = user.email
            username = user.username
            setVerifyEmail(!user.email.isNullOrBlank())
            name = user.name
            isBlocked = user.isBlocked
        }

        userToUpdate.setConnection(configuration.connection)
        return getManagementAPI().users().update(user.id, userToUpdate).executeAsync().asSuspended().get()
    }

    override suspend fun deleteUser(id: String) {
        getManagementAPI().users().delete(id).executeAsync().asSuspended().get()
    }

    override suspend fun removeFromTenant(id: String, tenant: String) {
        unassignRoles(id, getUserRolesInTenant(id, tenant).map { it.id })
    }

    override suspend fun assignRoles(id: String, rolesIds: List<String>) {
        if (rolesIds.isNotEmpty()) {
            getManagementAPI().users().addRoles(id, rolesIds).executeAsync().asSuspended().get()
        }
    }

    override suspend fun unassignRoles(id: String, rolesIds: List<String>) {
        if (rolesIds.isNotEmpty()) {
            getManagementAPI().users().removeRoles(id, rolesIds).executeAsync().asSuspended().get()
        }
    }

    override suspend fun getAllUserRoles(id: String): Collection<Role> = getManagementAPI().users().listRoles(id, null)
        .executeAsync().asSuspended().get().items

    override suspend fun getUserRolesInTenant(id: String, tenant: String): Collection<Role> =
        getManagementAPI().users().listRoles(id, null)
            .executeAsync().asSuspended().get().items.filter { it.name.startsWith("$tenant:") }

    override suspend fun validateAdministrationRolesRemoval(tenant: String, userRoles: Collection<RoleName>) {
        if (RoleName.TENANT_ADMINISTRATOR in userRoles) {
            validateAdministrationRoleRemoval(
                RoleName.TENANT_ADMINISTRATOR,
                userRoles,
                tenant
            ) { "The unique tenant administrator cannot be unassigned." }
        }
        if (RoleName.BILLING_ADMINISTRATOR in userRoles) {
            validateAdministrationRoleRemoval(
                RoleName.BILLING_ADMINISTRATOR,
                userRoles,
                tenant
            ) { "The unique billing administrator cannot be unassigned." }
        }
    }

    private suspend fun validateAdministrationRoleRemoval(
        roleToValidate: RoleName,
        userRoles: Collection<RoleName>,
        tenant: String, errorMessage: () -> String
    ) {
        if (roleToValidate in userRoles) {
            require(listUsersWithRoleInTenant(roleToValidate, tenant).size > 1, errorMessage)
        }
    }

    override suspend fun listUsersWithRoleInTenant(role: RoleName, tenant: String): Collection<User> {
        return roleRepository.findByTenantAndNameIn(tenant, listOf(role)).firstOrNull()?.reference?.let { roleId ->
            getManagementAPI().roles().listUsers(roleId, null).executeAsync().asSuspended().get().items
        } ?: emptyList()
    }

    /**
     * Creates new roles in Auth0 if they do not exist and return the mapping between values in [roles] and their IDs at Auth0.
     */
    override suspend fun listRolesIds(
        tenant: String,
        roles: Collection<RoleName>,
        createMissingRoles: Boolean
    ): List<String> {
        val existingRoles = roleRepository.findByTenantAndNameIn(tenant, roles)
        val existingRolesByReference = existingRoles.map { it.reference }.toMutableList()

        if (createMissingRoles) {
            val rolesToCreate = (roles.toSet() - existingRoles.map { it.name }.toSet())
            if (rolesToCreate.isNotEmpty()) {
                val createdRolesIds = rolesToCreate.associateWith { role ->
                    getManagementAPI().roles().create(Role().apply {
                        name = role.forTenant(tenant)
                        description = "Role $role for tenant $tenant"
                    }).executeAsync().asSuspended()
                }.mapValues { (_, createRole) -> createRole.get().id }
                existingRolesByReference += createdRolesIds.values

                roleRepository.saveAll(createdRolesIds.map { (roleName, createdRoleId) ->
                    RoleEntity(
                        tenant,
                        roleName,
                        createdRoleId
                    )
                }).collect()
            }
        }

        return existingRolesByReference
    }
}