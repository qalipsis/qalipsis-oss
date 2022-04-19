package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.auth0.Auth0PatchConverter
import io.qalipsis.core.head.security.entity.BillingAdminitratorRole
import io.qalipsis.core.head.security.entity.QalipsisRole
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.ReporterRole
import io.qalipsis.core.head.security.entity.RoleName
import io.qalipsis.core.head.security.entity.SuperAdministratorRole
import io.qalipsis.core.head.security.entity.TenantAdministratorRole
import io.qalipsis.core.head.security.entity.TesterRole
import io.qalipsis.core.head.security.entity.UserIdentity
import java.time.Instant

/**
 * There is a default implementation of [UserManagement] interface that encapsulate
 * the “identity management” enabled by configuration
 *
 * @author Palina Bril
 */
internal class UserManagementImpl(
    private val identityManagement: IdentityManagement,
    private val userRepository: UserRepository
) : UserManagement {

    override suspend fun get(tenant: String, username: String): QalipsisUser? {
        val userEntity = userRepository.findByUsername(username)
        return if (userEntity?.disabled == null) {
            val userIdentity = userEntity!!.identityReference?.let { identityManagement.get(tenant, it) }
            userIdentity?.let { QalipsisUser(it, userEntity) }
        } else {
            null
        }
    }

    override suspend fun save(tenant: String, user: QalipsisUser, userPatches: Collection<UserPatch>) {
        if (userPatches.asSequence().map { it.apply(user) }.any()) {
            userPatches.forEach { it.apply(user) }
            user.identityReference?.let {
                identityManagement.update(
                    tenant = tenant,
                    identityReference = it,
                    user = transformToUserIdentity(tenant, user),
                    userPatches = Auth0PatchConverter(userPatches).convert()
                )
            }
            userRepository.update(transformToUserEntity(user))
        }
    }

    override suspend fun delete(tenant: String, username: String) {
        val userEntity = userRepository.findByUsername(username)
        if (userEntity != null) {
            val disabledUser = userEntity.copy(disabled = Instant.now(), identityReference = null)
            userRepository.update(disabledUser)
            userEntity.identityReference?.let {
                identityManagement.delete(
                    tenant = tenant,
                    identityReference = it
                )
            }
        }
    }

    override suspend fun create(tenant: String, user: QalipsisUser) {
        val authUser =
            identityManagement.save(tenant = tenant, user = transformToUserIdentity(tenant, user))
        userRepository.save(transformToUserEntityFromUserIdentity(authUser))
    }

    override suspend fun getAssignableRoles(tenant: String, currentUser: QalipsisUser): Set<RoleName> {
        return currentUser.roles.flatMap {
            when (it) {
                RoleName.SUPER_ADMINISTRATOR -> listOf(
                    RoleName.SUPER_ADMINISTRATOR,
                    RoleName.BILLING_ADMINISTRATOR,
                    RoleName.TENANT_ADMINISTRATOR,
                    RoleName.TESTER,
                    RoleName.REPORTER
                )
                RoleName.BILLING_ADMINISTRATOR -> listOf(RoleName.BILLING_ADMINISTRATOR)
                RoleName.TENANT_ADMINISTRATOR -> listOf(
                    RoleName.TENANT_ADMINISTRATOR,
                    RoleName.TESTER,
                    RoleName.REPORTER
                )
                RoleName.TESTER -> listOf(
                    RoleName.TESTER,
                    RoleName.REPORTER
                )
                RoleName.REPORTER -> listOf(RoleName.REPORTER)
            }
        }.toSet()
    }

    private fun transformToQalipsisRoles(tenant: String, currentUser: QalipsisUser): Set<QalipsisRole> {
        return currentUser.roles.flatMap {
            when (it) {
                RoleName.SUPER_ADMINISTRATOR -> listOf(
                    SuperAdministratorRole(tenant),
                    BillingAdminitratorRole(tenant),
                    TenantAdministratorRole(tenant),
                    TesterRole(tenant),
                    ReporterRole(tenant)
                )
                RoleName.BILLING_ADMINISTRATOR -> listOf(BillingAdminitratorRole(tenant))
                RoleName.TENANT_ADMINISTRATOR -> listOf(
                    TenantAdministratorRole(tenant),
                    TesterRole(tenant),
                    ReporterRole(tenant)
                )
                RoleName.TESTER -> listOf(TesterRole(tenant), ReporterRole(tenant))
                RoleName.REPORTER -> listOf(ReporterRole(tenant))
            }
        }.toSet()
    }

    private fun transformToUserEntity(user: QalipsisUser): UserEntity {
        return UserEntity(
            id = user.userEntityId,
            version = user.version,
            creation = user.creation,
            username = user.username,
            identityReference = user.identityReference,
            disabled = user.disabled
        )
    }

    private fun transformToUserIdentity(tenant: String, user: QalipsisUser): UserIdentity {
        return UserIdentity(
            username = user.username,
            email = user.email,
            name = user.name,
            email_verified = user.email_verified,
            connection = user.connection,
            verify_email = user.verify_email,
            password = user.password,
            userRoles = transformToQalipsisRoles(tenant, user).toList()
        )
    }

    private fun transformToUserEntityFromUserIdentity(user: UserIdentity): UserEntity {
        return UserEntity(
            username = user.username,
            identityReference = user.user_id
        )
    }
}