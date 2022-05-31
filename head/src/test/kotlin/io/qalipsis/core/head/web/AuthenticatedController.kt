package io.qalipsis.core.head.web

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotations.Tenant

/**
 * Controller used for testing the security resolution in different use cases.
 *
 * @author Eric Jessé
 */
@Controller("/")
internal class AuthenticatedController {

    @Get("/unsecure")
    @Secured(SecurityRule.IS_ANONYMOUS)
    suspend fun unsecure(
        @Tenant tenant: String,
        authentication: Authentication
    ): CallResult {
        return CallResult(
            tenant,
            authentication.name,
            authentication.roles,
            authentication.attributes
        )
    }

    @Get("/secure")
    @Secured(value = [Permissions.CREATE_CAMPAIGN])
    suspend fun secure(
        @Tenant tenant: String,
        authentication: Authentication
    ): CallResult {
        return CallResult(
            tenant,
            authentication.name,
            authentication.roles,
            authentication.attributes
        )
    }

    @Introspected
    data class CallResult(
        val tenant: String,
        val name: String,
        val roles: Collection<String>,
        val attributes: Map<String, Any>
    )
}