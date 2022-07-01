package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.report.DataSeriesService
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotation.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

@Validated
@Controller("/data-series")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [DataSeriesService::class])
)
@Version("1.0")
internal class DataSeriesController(
    private val dataSeriesService: DataSeriesService
) {

    @Post
    @Operation(
        summary = "Create a new data series",
        description = "Create a new data series with the provided details and attach it to the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully created data series"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @Timed("data-series-create")
    suspend fun create(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid dataSeries: DataSeries
    ): HttpResponse<DataSeries> {
        return HttpResponse.ok(
            dataSeriesService.create(
                creator = authentication.name,
                tenant = tenant,
                dataSeries = dataSeries
            )
        )
    }

    @Get("/{reference}")
    @Operation(
        summary = "Retrieve a unique data series",
        description = "Return a unique data series attached to the tenant, containing its details",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the data series in the tenant"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "Data series not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_DATA_SERIES)
    @Timed("data-series-retrieve")
    suspend fun get(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the data series to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reference: String
    ): HttpResponse<DataSeries> {
        return HttpResponse.ok(
            dataSeriesService.get(
                username = authentication.name,
                tenant = tenant,
                reference = reference
            )
        )
    }

    @Patch("/{reference}")
    @Operation(
        summary = "Update a data series",
        description = "Update the details a data series for the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Updated data series"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "Data series not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_DATA_SERIES)
    @Timed("data-series-update")
    suspend fun update(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the data series to update",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable reference: String,
        @NotEmpty @Body dataSeriesPatches: List<@Valid DataSeriesPatch>
    ): HttpResponse<DataSeries> {
        return HttpResponse.ok(
            dataSeriesService.update(
                username = authentication.name,
                tenant = tenant,
                reference = reference,
                patches = dataSeriesPatches
            )
        )
    }

    @Delete("/{reference}")
    @Operation(
        summary = "Delete a data series",
        description = "Delete a data series from the contextual tenant",
        responses = [
            ApiResponse(responseCode = "204", description = "Successful deletion "),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @Timed("data-series-delete")
    suspend fun delete(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the data series to delete",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reference: String
    ): HttpResponse<Unit> {
        dataSeriesService.delete(username = authentication.name, tenant = tenant, reference = reference)
        return HttpResponse.status(HttpStatus.ACCEPTED)
    }
}