package io.qalipsis.core.head.security

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Details of a QALIPSIS user.
 *
 * @author Palina Bril
 */
@Schema(name = "User", title = "User of QALIPSIS", description = "Details of a QALIPSIS user")
internal data class User(
    @field:Schema(description = "Tenant owning the user", required = false)
    val tenant: String,

    @field:Schema(description = "Unique identifier of the user")
    val username: String

)