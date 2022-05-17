package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.security.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Model to create a new user.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(name = "User creation request", title = "Details for the creation of a new user into QALIPSIS")
internal data class UserCreationRequest(
    @field:Schema(description = "Unique name of the user into QALIPSIS")
    @field:NotBlank
    @field:Size(min = 1, max = 150)
    val username: String,

    @field:Schema(description = "Name to use to display the identity")
    @field:NotBlank
    @field:Size(min = 1, max = 150)
    val displayName: String,

    @field:Schema(description = "Email address for the user, it should be unique in QALIPSIS")
    @field:NotBlank
    @field:Email
    val email: String,

    @field:Schema(description = "Set to true when the user is not allowed to sign in")
    val blocked: Boolean = false,

    @field:Schema(description = "Roles to assign to the user at the creation")
    val roles: Collection<RoleName> = emptySet()
) {
    fun toUser(tenant: String): User {
        return User(
            tenant = tenant,
            username = username,
            version = Instant.now(),
            creation = Instant.now(),
            email = email,
            displayName = displayName,
            emailVerified = false,
            blocked = blocked,
            roles = roles
        )
    }
}