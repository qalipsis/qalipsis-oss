package io.qalipsis.core.head.security.auth0

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.NotBlank

/**
 * Configuration for [Auth0IdentityManagement].
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
    val apiUrl: String

    @get:NotBlank
    val domain: String

    @get:NotBlank
    @get:Bindable(defaultValue = "Username-Password-Authentication")
    val connection: String
}
