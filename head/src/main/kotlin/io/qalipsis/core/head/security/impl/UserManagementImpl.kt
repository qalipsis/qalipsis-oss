package io.qalipsis.core.head.security.impl

import com.auth0.json.mgmt.users.User
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.entity.QalipsisUser
import java.time.Instant

/**
 * There is a default implementation of [UserManagement] interface that encapsulate
 * the “identity management” enabled by configuration
 *
 * @author Palina Bril
 */
class UserManagementImpl(
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
        userRepository.save(transformToUserEntity(QalipsisUser(authUser)))
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

    private fun transformToUserIdentity(user: QalipsisUser): User {
        val authUser = User()
        authUser.email = user.email
        authUser.username = user.username
        authUser.name = user.name
        authUser.isEmailVerified = user.email_verified
        authUser.setVerifyEmail(user.verify_email)
        authUser.setPassword((user.password).toCharArray())
        authUser.setConnection(user.connection)
        return authUser
    }
}