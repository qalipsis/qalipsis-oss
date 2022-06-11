package io.qalipsis.core.head.security.auth0

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.model.SecurityConfiguration
import jakarta.inject.Singleton


@Factory
@Requires(beans = [Auth0Configuration::class])
internal class Auth0Factory(private val auth0Configuration: Auth0Configuration) {

    @Singleton
    internal fun securityConfiguration(): SecurityConfiguration {
        return Auth0SecurityConfiguration(
            authorizationUrl = auth0Configuration.oauth2.authorizationUrl,
            revocationUrl = auth0Configuration.oauth2.revocationUrl,
            scopes = listOf("openid", "profile", auth0Configuration.oauth2.roles, auth0Configuration.oauth2.tenants),
            clientId = auth0Configuration.oauth2.clientId,
            domain = auth0Configuration.domain
        )
    }
}