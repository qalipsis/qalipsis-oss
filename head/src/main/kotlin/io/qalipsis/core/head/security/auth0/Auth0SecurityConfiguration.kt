package io.qalipsis.core.head.security.auth0

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.model.OAuth2SecurityConfiguration
import io.qalipsis.core.head.model.SecurityStrategy
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Implementation of [OAuth2SecurityConfiguration] matching Auth0 configuration.
 *
 * @author Eric Jessé
 */
@Introspected
internal data class Auth0SecurityConfiguration(
    override val authorizationUrl: String,
    override val revocationUrl: String,
    override val scopes: Collection<String>,
    @get:Schema(description = "Client ID of the Auth0 authorize API")
    val clientId: String,
    @get:Schema(description = "Domain of the Auth0 tenant")
    val domain: String,
) : OAuth2SecurityConfiguration {

    override val strategy: SecurityStrategy = SecurityStrategy.AUTH0

}