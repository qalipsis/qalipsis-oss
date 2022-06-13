package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.security.User
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Profile details of a QALIPSIS user.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Profile",
    title = "Profile of a QALIPSIS user",
    description = "Details of a QALIPSIS user and its profile"
)
internal data class Profile(
    @field:Schema(description = "Details of the user")
    val user: User,

    @field:Schema(description = "Tenants accessible to the user")
    val tenants: Collection<Tenant>
)
