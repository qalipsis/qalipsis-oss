package io.qalipsis.core.head.security.auth0

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import javax.validation.constraints.NotBlank

/**
 * Configuration for [IdentityManagement] Auth0.
 *
 * @property connection is Auth0 connection for saving users.
 * @property token to connect to the Auth0 identity manager.
 * @property baseAddress for working with Auth0 users.
 * @property clientId, clientSecret and apiIdentifier are needed to receive new token to Auth0 identity manager.
 *
 * @author Palina Bril
 */
@Requires(property = "identity.manager", value = "auth0")
@ConfigurationProperties("identity.auth0")
internal interface Auth0Configuration {

    @get:NotBlank
    val clientId: String

    @get:NotBlank
    val clientSecret: String

    @get:NotBlank
    val apiIdentifier: String

    @get:NotBlank
    val domain: String
}
