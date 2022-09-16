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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.DataField
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.report.DataProvider
import io.qalipsis.core.head.report.DataSeriesService
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.ControllerUtils.asFilters
import io.qalipsis.core.head.web.annotation.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

@Validated
@Controller("/data-series")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [DataSeriesService::class])
)
@Version("1.0")
internal class DataSeriesController(
    private val dataSeriesService: DataSeriesService,
    private val dataProvider: DataProvider
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

    @Get("/{data-type}/names")
    @Operation(
        summary = "List some events or meters names to help with auto-completion",
        responses = [
            ApiResponse(responseCode = "200", description = "List of first names matching the filter"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    suspend fun searchDataNames(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of the data related to the tags to search",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
        @Parameter(
            description = "Comma-separated list of values to apply as wildcard filters on the names",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Size of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue(defaultValue = "20") @Positive @Max(100) size: Int
    ): Collection<String> {
        return dataProvider.searchNames(tenant, dataType, filter.asFilters(), size)
    }

    @Get("/{data-type}/fields")
    @Operation(
        summary = "List all the fields that can be used for charts of events or meters",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "List of fields that can be used in reports charts, with their types and units"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    suspend fun listDataFields(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of the data related to the tags to search",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
    ): Collection<DataField> {
        return dataProvider.listFields(tenant, dataType)
    }

    @Get("/{data-type}/tags")
    @Operation(
        summary = "List some tags to help with auto-completion",
        responses = [
            ApiResponse(responseCode = "200", description = "List of first tags matching the filter and their values"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    suspend fun searchDataTags(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of the data related to the tags to search",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
        @Parameter(
            description = "Comma-separated list of values to apply as wildcard filters on the tags names",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Size of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue(defaultValue = "20") @Positive @Max(100) size: Int
    ): Map<String, Collection<String>> {
        return dataProvider.searchTagsAndValues(tenant, dataType, filter.asFilters(), size)
    }

    @Get("/")
    @Operation(
        summary = "Search all the data series",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "List of data series that matches the search parameters"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_DATA_SERIES])
    suspend fun searchDataSeries(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Sorting property and order, example: name:DESC",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("sort", defaultValue = "") sort: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Comma-separated list of values to apply as wildcard filters on the data series fields",
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
        ) @QueryValue("size", defaultValue = "20") @Positive @Max(100) size: Int
    ): HttpResponse<Page<DataSeries>> {
        return HttpResponse.ok(
            dataSeriesService.searchDataSeries(
                tenant,
                authentication.name,
                filter.asFilters(),
                sort.takeUnless(String::isNullOrBlank),
                page,
                size
            )
        )
    }

}