package io.qalipsis.core.head.security.impl

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.entity.BillingAdminitratorRole
import io.qalipsis.core.head.security.entity.QalipsisRole
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.ReporterRole
import io.qalipsis.core.head.security.entity.RoleNames
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

    override suspend fun get(username: String): QalipsisUser? {
        val userEntity = userRepository.findByUsername(username)
        return if (userEntity?.disabled == null) {
            val userIdentity = userEntity!!.identityReference?.let { identityManagement.get(it) }
            userIdentity?.let { QalipsisUser(it, userEntity) }
        } else {
            null
        }
    }

    override suspend fun save(user: QalipsisUser, userPatches: Collection<UserPatch>) {
        if (userPatches.asSequence().map { it.apply(user) }.any()) {
            user.identityReference?.let { identityManagement.update(it, transformToUserIdentity(user)) }
            userRepository.update(transformToUserEntity(user))
        }
    }

    override suspend fun delete(username: String) {
        val userEntity = userRepository.findByUsername(username)
        if (userEntity != null) {
            val disabledUser = userEntity.copy(disabled = Instant.now(), identityReference = null)
            userRepository.update(disabledUser)
            userEntity.identityReference?.let { identityManagement.delete(it) }
        }
    }

    override suspend fun create(user: QalipsisUser) {
        val authUser = identityManagement.save(transformToUserIdentity(user))
        userRepository.save(transformToUserEntityFromUserIdentity(authUser))
    }

    override suspend fun getAssignableRoles(currentUser: QalipsisUser): Set<QalipsisRole> {
        val currentUserRoles = mutableSetOf<QalipsisRole>()
        currentUser.roles.forEach {
            when (it) {
                RoleNames.SUPER_ADMINISTRATOR -> currentUserRoles.addAll(
                    listOf(
                        SuperAdministratorRole(),
                        BillingAdminitratorRole(),
                        TenantAdministratorRole(),
                        TesterRole(),
                        ReporterRole()
                    )
                )
                RoleNames.BILLING_ADMINISTRATOR -> currentUserRoles.addAll(listOf(BillingAdminitratorRole()))
                RoleNames.TENANT_ADMINISTRATOR -> currentUserRoles.addAll(
                    listOf(
                        TenantAdministratorRole(),
                        TesterRole(),
                        ReporterRole()
                    )
                )
                RoleNames.TESTER -> currentUserRoles.addAll(listOf(TesterRole(), ReporterRole()))
                RoleNames.REPORTER -> currentUserRoles.addAll(listOf(ReporterRole()))
            }
        }
        return currentUserRoles
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

    private suspend fun transformToUserIdentity(user: QalipsisUser): UserIdentity {
        return UserIdentity(
            username = user.username,
            email = user.email,
            name = user.name,
            email_verified = user.email_verified,
            connection = user.connection,
            verify_email = user.verify_email,
            password = user.password,
            userRoles = getAssignableRoles(user).toList()
        )
    }

    private fun transformToUserEntityFromUserIdentity(user: UserIdentity): UserEntity {
        return UserEntity(
            username = user.username,
            identityReference = user.user_id
        )
    }
}