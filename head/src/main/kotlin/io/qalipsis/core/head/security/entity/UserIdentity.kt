package io.qalipsis.core.head.security.entity

/**
 * User details for saving in identity manager
 *
 * @author Palina Bril
 */
data class UserIdentity(
    var username: String,
    var email: String,
    var name: String,
    var connection: String = "Username-Password-Authentication",
    var verify_email: Boolean = true,
    var email_verified: Boolean = false,
    var password: String = "pass",
    var user_id: String = "",
    var userRoles: List<QalipsisRole> = mutableListOf()
)
