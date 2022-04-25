package io.qalipsis.core.head.security.auth0

/**
 * User details for saving in Auth0 identity manager
 *
 * @author Palina Bril
 */
data class Auth0User(
    var username: String,
    var email: String,
    var name: String,
    var connection: String = "qalipsis",
    var verify_email: Boolean = true,
    var email_verified: Boolean = false,
    var password: String = "pass"
){
    constructor(
      email: String,
      name: String,
      username: String
      ) : this(
        username, email, name, "qalipsis", true, false, "pass"
    )
}
