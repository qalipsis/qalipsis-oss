/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.api.query.Page
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import io.qalipsis.core.head.model.ReportTask
import io.qalipsis.core.head.report.ReportService
import io.qalipsis.core.head.web.ControllerUtils.asFilters
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

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
@Tag(name = "Report management")
class ReportController(
    private val reportService: ReportService,
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
        @Body @Valid reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest,
    ): Report {
        return reportService.create(
            tenant = tenant,
            creator = authentication.name,
            reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
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
        ) @NotBlank @PathVariable reference: String,
    ): Report {
        return reportService.get(
            tenant = tenant,
            username = authentication.name,
            reference = reference
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
        @Body @Valid reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest,
    ): Report {
        return reportService.update(
            tenant = tenant,
            username = authentication.name,
            reference = reference,
            reportCreationAndUpdateRequest = reportCreationAndUpdateRequest
        )
    }

    @Delete
    @Operation(
        summary = "Deletes a list report",
        description = "Deletes a list of existing report template",
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
    @Status(HttpStatus.ACCEPTED)
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
            `in` = ParameterIn.QUERY
        ) @QueryValue("report") references: Set<@NotBlank String>,
    ) {
        reportService.delete(tenant = tenant, username = authentication.name, references = references)
    }

    @Get
    @Operation(
        summary = "Search all the reports",
        responses = [
            ApiResponse(responseCode = "200", description = "Page of reports matching the criteria"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_REPORT])
    @Timed("reports-search")
    suspend fun search(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Field of the report to use in order to sort the results",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("sort", defaultValue = "") sort: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Comma-separated list of values to apply as wildcard filters on the reports fields",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Page number to start retrieval from",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("page", defaultValue = "0") @PositiveOrZero page: Int,
        @Parameter(
            description = "Size of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("size", defaultValue = "20") @Positive @Max(100) size: Int,
    ): Page<Report> {
        return reportService.search(
            tenant,
            authentication.name,
            filter.asFilters(),
            sort.takeUnless(String::isNullOrBlank),
            page,
            size
        )
    }

    @Post("/{reportReference}/render")
    @Operation(
        summary = "Renders a report",
        description = "Generates a pdf report and returns a reference to the report task",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the report to be rendered"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "Report not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_REPORT])
    suspend fun render(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the report to render",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reportReference: String,
    ): ReportTask {
        return reportService.render(
            tenant,
            authentication.name,
            reportReference
        )
    }

    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Get("/file/{taskReference}")
    @Operation(
        summary = "Downloads a report",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the report to be rendered"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
            ApiResponse(responseCode = "404", description = "report not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_REPORT])
    @Status(HttpStatus.OK)
    suspend fun download(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the task to be downloaded",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable taskReference: String,
    ): ByteArray {
        val response = reportService.read(
            tenant,
            authentication.name,
            taskReference
        )

        return response.content
    }
}