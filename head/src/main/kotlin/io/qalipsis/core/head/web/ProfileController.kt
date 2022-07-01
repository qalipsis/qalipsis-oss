package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Profile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement

/**
 * API to provide details about the current user.
 *
 * @author Eric Jessé
 */
@Validated
@Version("1.0")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller
internal class ProfileController {

    @Operation(
        summary = "User profile",
        description = "Provide the different details of the current user",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of available zones"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Get("/users/profile")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-profile")
    suspend fun profile(authentication: Authentication): HttpResponse<Profile> {
        return HttpResponse.ok(Defaults.PROFILE)
    }

    @Operation(
        summary = "Permissions",
        description = "Provide the list of permissions, that are assigned to the current user in the current context",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of available zones"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Get("/users/permissions")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-permissions")
    suspend fun permissions(authentication: Authentication): HttpResponse<Collection<String>> {
        return HttpResponse.ok(authentication.roles)
    }
}