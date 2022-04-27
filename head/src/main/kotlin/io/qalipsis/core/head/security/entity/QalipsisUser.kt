package io.qalipsis.core.head.security.entity

import com.auth0.json.mgmt.users.User
import io.qalipsis.core.head.jdbc.entity.UserEntity
import java.time.Instant

/**
 * User details from UserEntity and UserIdentity
 *
 * @author Palina Bril
 */
data class QalipsisUser(
    var username: String,
    var email: String,
    var name: String,
    var connection: String = "Username-Password-Authentication",
    var verify_email: Boolean = true,
    var email_verified: Boolean = false,
    var password: String = "pass",
    var userEntityId: Long,
    var version: Instant,
    var creation: Instant,
    var identityReference: String? = null,
    var disabled: Instant? = null
) {
    constructor(user: User) : this(
        username = user.username,
        email = user.email,
        name = user.name,
        email_verified = user.isEmailVerified,
        identityReference = user.id,
        userEntityId = -1,
        version = Instant.EPOCH,
        creation = Instant.now(),
    )

    constructor(authUser: User, userEntity: UserEntity) : this(
        username = authUser.username,
        email = authUser.email,
        name = authUser.name,
        email_verified = authUser.isEmailVerified,
        userEntityId = userEntity.id,
        version = userEntity.version,
        creation = userEntity.creation,
        identityReference = userEntity.identityReference,
        disabled = userEntity.disabled
    )
}
