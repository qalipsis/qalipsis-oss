package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.validation.Validated
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.web.annotations.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Validated
@Controller("/scenarios")
@Version("1.0")
internal class ScenarioController(
    private val factoryService: FactoryService
) {

    @Get
    @Operation(
        summary = "Get a list of available scenarios",
        description = "Get a list of available scenarios of the tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the available scenarios"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun listScenarios(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Field of the entity which will be used for sorting",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue sort: String
    ): HttpResponse<List<ScenarioSummary>> {
        return HttpResponse.ok(factoryService.getAllActiveScenarios(tenant, sort).toList())
    }
}