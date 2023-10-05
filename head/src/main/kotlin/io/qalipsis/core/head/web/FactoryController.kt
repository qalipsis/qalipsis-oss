package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.validation.Validated
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.report.FactoryState
import io.qalipsis.core.head.report.WidgetService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement

/**
 * Controller for REST calls related to factory operations.
 *
 * @author Francisca Eze
 */
@Controller("/factories")
@Version("1.0")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [WidgetService::class])
)
@Validated
internal class FactoryController(
    private val widgetService: WidgetService
) {

    @Get("/states")
    @Operation(
        summary = "Retrieve the factory states",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "success response with the factories states contained in the response body"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = " not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Timed("factory-retrieve")
    suspend fun getFactoryStates(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
    ): FactoryState {
        return widgetService.getFactoryStates(tenant)
    }
}