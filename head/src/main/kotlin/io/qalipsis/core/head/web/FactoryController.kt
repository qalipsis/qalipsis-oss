package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.report.FactoryState
import io.qalipsis.core.head.report.WidgetService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Controller for REST calls related to factory operations.
 *
 * @author Francisca Eze
 */
@Controller("\${server.api-root}/factories")
@Version("1.0")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [WidgetService::class])
)
@Validated
@Tag(name = "Factory states management")
class FactoryController(
    private val widgetService: WidgetService
) {

    @Get("/states")
    @Operation(
        summary = "Retrieve factory states",
        description = "Returns the current states of the factory for the contextual tenant.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Factory states retrieved successfully."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
            ApiResponse(responseCode = "404", description = "Factory states not found."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Timed("factory-retrieve")
    @Secured(value = [Permissions.READ_CAMPAIGN])
    suspend fun getFactoryStates(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
    ): FactoryState {
        return widgetService.getFactoryStates(tenant)
    }
}