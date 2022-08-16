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
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import io.qalipsis.core.head.report.ReportService
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotation.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.validation.Valid
import javax.validation.constraints.NotBlank

/**
 * @author Joël Valère
 */

@Validated
@Controller("/reports")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [ReportService::class])
)
@Version("1.0")
internal class ReportController(
    private val reportService: ReportService
) {

    @Post
    @Operation(
        summary = "Create a new report",
        description = "Create a new report template containing analysis components for several campaigns",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully created report"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_REPORT])
    @Timed("report-create")
    suspend fun create(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest
    ): HttpResponse<Report> {
        return HttpResponse.ok(
            reportService.create(
                tenant = tenant,
                creator = authentication.name,
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        )
    }

    @Get("/{reference}")
    @Operation(
        summary = "Retrieve a unique report",
        description = "Retrieve a single report template and all its details",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the report in the tenant"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "report not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_REPORT)
    @Timed("report-retrieve")
    suspend fun get(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the report to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reference: String
    ): HttpResponse<Report> {
        return HttpResponse.ok(
            reportService.get(
                tenant = tenant,
                username = authentication.name,
                reference = reference
            )
        )
    }

    @Put("/{reference}")
    @Operation(
        summary = "Update an existing report",
        description = "Update an existing report template",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully updated report"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "Report not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_REPORT])
    @Timed("report-update")
    suspend fun update(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the report to update",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable reference: String,
        @Body @Valid reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest
    ): HttpResponse<Report> {
        return HttpResponse.ok(
            reportService.update(
                tenant = tenant,
                username = authentication.name,
                reference = reference,
                reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
            )
        )
    }

    @Delete("/{reference}")
    @Operation(
        summary = "Delete a report",
        description = "Delete an existing report template",
        responses = [
            ApiResponse(responseCode = "204", description = "Successful deletion "),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_REPORT])
    @Timed("report-delete")
    suspend fun delete(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the report to delete",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reference: String
    ): HttpResponse<Unit> {
        reportService.delete(tenant = tenant, username = authentication.name, reference = reference)
        return HttpResponse.status(HttpStatus.ACCEPTED)
    }
}