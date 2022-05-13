package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity
import java.time.Instant

/**
 * User details from UserEntity and UserIdentity
 *
 * @author Palina Bril
 */
internal data class User(
    val tenant: String,
    val username: String,
    val version: Instant,
    val creation: Instant,
    val displayName: String,
    val email: String,
    val emailVerified: Boolean = false,
    val blocked: Boolean = false,
    val roles: Collection<RoleName> = emptySet()
) {

    constructor(tenant: String, entity: UserEntity, identity: UserIdentity) : this(
        tenant = tenant,
        username = identity.username,
        version = entity.version,
        creation = entity.creation,
        displayName = identity.displayName,
        email = identity.email,
        emailVerified = identity.emailVerified,
        blocked = identity.blocked,
        roles = identity.roles
    )
}
