package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.SecurityConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse


/**
 * Controller for REST calls related to retrieving the configuration, for example, security configuration.
 *
 * @author Palina Bril
 */
@Validated
@Controller("/configuration")
@Requires(env = [ExecutionEnvironments.HEAD])
@Version("1.0")
internal class ConfigurationController(
    private val securityConfiguration: SecurityConfiguration
) {

    /**
     * REST endpoint to get security configuration.
     */
    @Get("/security")
    @Operation(
        summary = "Security configuration",
        description = "Provide the details of the strategy model to apply in the frontend and its configuration",
        responses = [
            ApiResponse(responseCode = "200", description = "Security configuration")
        ]
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    suspend fun retrieveSecurity(): HttpResponse<SecurityConfiguration> {
        return HttpResponse.ok(securityConfiguration)
    }
}