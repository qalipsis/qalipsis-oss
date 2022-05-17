package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Details of the user as persisted into QALIPSIS.
 *
 * @author Palina Bril
 */
@Schema(name = "User", title = "User of QALIPSIS", description = "Details of the user as persisted into QALIPSIS")
internal data class User(
    @Schema(description = "Tenant owning the user", required = false)
    val tenant: String,
    @Schema(description = "Unique identifier of the user")
    val username: String,
    @Schema(description = "Last change of the user metadata in QALIPSIS")
    val version: Instant,
    @Schema(description = "Creation of the user in QALIPSIS")
    val creation: Instant,
    @Schema(description = "Name to display for the user")
    val displayName: String,
    @Schema(description = "Email address")
    val email: String,
    @Schema(description = "Indicates whether the email address was already verified")
    val emailVerified: Boolean = false,
    @Schema(description = "Indicates whether the user is blocked or granted to access to QALIPSIS")
    val blocked: Boolean = false,
    @Schema(description = "Roles of the user to grant access to QALIPSIS")
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
