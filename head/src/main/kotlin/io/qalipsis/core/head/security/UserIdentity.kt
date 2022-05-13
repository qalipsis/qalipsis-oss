package io.qalipsis.core.head.security

/**
 * User details for saving in identity manager
 *
 * @author Palina Bril
 */
internal data class UserIdentity(
    val id: String = "",
    var username: String,
    var email: String,
    var displayName: String,
    val verifyEmail: Boolean = true,
    val emailVerified: Boolean = false,
    val blocked: Boolean = false,
    val roles: MutableSet<RoleName> = mutableSetOf()
)
