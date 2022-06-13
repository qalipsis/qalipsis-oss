package io.qalipsis.core.head.security.auth0

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.ServerAuthentication
import io.micronaut.security.filters.AuthenticationFetcher
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.security.UserManagement
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Normalizes Auth0 authentication relatively to the contextual tenant.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [Auth0Configuration::class])
internal class Auth0AuthenticationConverterSecurityRule(
    private val userManagement: UserManagement,
    private val authenticationFetchers: Collection<AuthenticationFetcher>,
    private val auth0OAuth2Configuration: Auth0Configuration.Auth0OAuth2Configuration
) : AuthenticationFetcher {

    override fun fetchAuthentication(request: HttpRequest<*>): Publisher<Authentication> {
        return Flux.fromIterable(authenticationFetchers)
            .flatMap { authenticationFetcher -> authenticationFetcher.fetchAuthentication(request) }
            .next()
            .flatMap { authentication ->
                if (tokenHasNameAsSubject(authentication)) {
                    Mono.just(authentication to authentication.name)
                } else {
                    // The real user has to be searched in the DB.
                    Mono.from(flow<Pair<Authentication, String>> {
                        emit(authentication to userManagement.getUsernameFromIdentityId(authentication.name))
                    }.asPublisher())
                }
            }
            .flatMap { (authentication, username) ->
                val tenant = request.headers.get("X-Tenant")
                val permissions = authentication.roles.asSequence()
                    .filter { role -> role.startsWith("*:") || (tenant != null && role.startsWith("${tenant}:")) }
                    .map { role -> RoleName.fromPublicName(role.substringAfter(":")) }
                    .flatMap { role -> role.permissions }
                    .toSet() + Permissions.AUTHENTICATED

                Mono.just(
                    ServerAuthentication(
                        username,
                        permissions,
                        authentication.attributes + ("tenants" to authentication.attributes[auth0OAuth2Configuration.tenants])
                    )
                )
            }
    }

    /**
     * Verifies whether the profile scope is part of the token, which is the case when the attribute nickname is set.
     */
    private fun tokenHasNameAsSubject(authentication: Authentication) =
        authentication.attributes.containsKey("nickname")

    /**
     * Should be executed once the token was extracted.
     */
    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }

    private companion object {

        val log = logger()
    }

}