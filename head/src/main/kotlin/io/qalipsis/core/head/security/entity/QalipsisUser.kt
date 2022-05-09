package io.qalipsis.core.head.security.entity

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.jdbc.entity.UserEntity
import java.time.Instant

/**
 * User details from UserEntity and UserIdentity
 *
 * @author Palina Bril
 */
@Introspected
internal data class QalipsisUser(
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
    var disabled: Instant? = null,
    var roles: List<RoleName> = mutableListOf()
) {
    constructor(user: UserIdentity) : this(
        username = user.username,
        email = user.email,
        name = user.name,
        email_verified = user.email_verified,
        identityReference = user.user_id,
        userEntityId = -1,
        version = Instant.EPOCH,
        creation = Instant.now(),
        roles = user.userRoles.map { RoleName.valueOf(it.name) }.toList()
    )

    constructor(authUser: UserIdentity, userEntity: UserEntity) : this(
        username = authUser.username,
        email = authUser.email,
        name = authUser.name,
        email_verified = authUser.email_verified,
        userEntityId = userEntity.id,
        version = userEntity.version,
        creation = userEntity.creation,
        identityReference = userEntity.identityReference,
        disabled = userEntity.disabled,
        roles = authUser.userRoles.map { RoleName.valueOf(it.name) }.toList()
    )
}
