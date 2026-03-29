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
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.validation.Validated
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.DataField
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.report.DataProvider
import io.qalipsis.core.head.report.DataSeriesService
import io.qalipsis.core.head.report.DataType
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
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero


@Validated
@Controller("\${server.api-root}/data-series")
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(beans = [DataSeriesService::class])
)
@Version("1.0")
@Tag(name = "Data-Series management")
class DataSeriesController(
    private val dataSeriesService: DataSeriesService,
    private val dataProvider: DataProvider,
) {

    @Post
    @Operation(
        summary = "Create data series",
        description = "Creates a new data series for the specified tenant using the provided details.",
        responses = [
            ApiResponse(responseCode = "200", description = "Returns the created data series."),
            ApiResponse(responseCode = "400", description = "Invalid request body or parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @Timed("data-series-create")
    @LogInputAndOutput
    suspend fun create(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Body @Valid dataSeries: DataSeriesCreationRequest,
    ): DataSeries {
        return dataSeriesService.create(
            tenant = tenant,
            creator = authentication.name,
            dataSeries = dataSeries
        )
    }

    @Get("/{reference}")
    @Operation(
        summary = "Retrieve data series",
        description = "Returns the details of a data series for the specified tenant by its reference.",
        responses = [
            ApiResponse(responseCode = "200", description = "Data series retrieved successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
            ApiResponse(responseCode = "404", description = "Data series not found."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.READ_DATA_SERIES)
    @Timed("data-series-retrieve")
    @LogInputAndOutput
    suspend fun get(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the data series to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable reference: String,
    ): DataSeries {
        return dataSeriesService.get(
            tenant = tenant,
            username = authentication.name,
            reference = reference
        )
    }

    @Patch("/{reference}")
    @Operation(
        summary = "Update data series",
        description = "Updates the specified data series for the tenant with the provided details.",
        responses = [
            ApiResponse(responseCode = "200", description = "Data series updated successfully."),
            ApiResponse(responseCode = "400", description = "Invalid body or request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
            ApiResponse(responseCode = "404", description = "Data series not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_DATA_SERIES)
    @Timed("data-series-update")
    @LogInputAndOutput
    suspend fun update(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Reference of the data series to update.",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable reference: String,
        @NotEmpty @Body dataSeriesPatches: List<@Valid DataSeriesPatch>,
    ): DataSeries {
        return dataSeriesService.update(
            tenant = tenant,
            username = authentication.name,
            reference = reference,
            patches = dataSeriesPatches
        )
    }

    @Delete
    @Operation(
        summary = "Delete data series",
        description = "Deletes one or more data series for the contextual tenant.",
        responses = [
            ApiResponse(responseCode = "204", description = "Data series deleted successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @Timed("data-series-delete")
    @Status(HttpStatus.ACCEPTED)
    @LogInputAndOutput
    suspend fun delete(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "References of the data series to delete; multiple references allowed, Example: series=w65dqw&series=1265fs2.",
            required = true,
            `in` = ParameterIn.QUERY
        ) @QueryValue("series") references: Set<@NotBlank String>,
    ) {
        dataSeriesService.delete(tenant = tenant, username = authentication.name, references = references)
    }

    @Get("/{data-type}/names")
    @Operation(
        summary = "Retrieve data names",
        description = "Returns a list of event or meter names for the specified data type to help with auto-completion.",
        responses = [
            ApiResponse(responseCode = "200", description = "Event or meter names retrieved successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @LogInputAndOutput
    suspend fun searchDataNames(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of data related to the tags to search. Options: `events` or `meters`.",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
        @Parameter(
            description = "Campaign key to filter the names by.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("campaign") campaignKey: String? = null,
        @Parameter(
            description = "Comma-separated list of filters to apply to names.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Number of data names per page.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue(defaultValue = "20") @Positive @Max(100) size: Int,
    ): Collection<String> {
        return dataProvider.searchNames(tenant, dataType, campaignKey, filter.asFilters(), size)
    }

    @Get("/{data-type}/fields")
    @Operation(
        summary = "Retrieve data fields",
        description = "Returns all fields that can be used for charts of the specified data type, including types and units. Data-type options include: `events` or `meters`.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Data fields successfully retrieved."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @LogInputAndOutput
    suspend fun listDataFields(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of data related to the tags to search. Options: `events` or `meters`.",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
        @Parameter(
            description = "Event or meter name to filter the fields by.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("name") name: String? = null,
    ): Collection<DataField> {
        return dataProvider.listFields(tenant, dataType, name)
    }

    @Get("/{data-type}/tags")
    @Operation(
        summary = "Retrieve data tags",
        description = "Returns a list of tags and their values for the specified data type to help with auto-completion.",
        responses = [
            ApiResponse(responseCode = "200", description = "Data tags retrieved successfully."),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.WRITE_DATA_SERIES])
    @LogInputAndOutput
    suspend fun searchDataTags(
        @Tenant tenant: String,
        @Parameter(
            name = "data-type",
            description = "Type of data related to the tags to search. Options: `events` or `meters`.",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable("data-type") dataType: DataType,
        @Parameter(
            description = "Event or meter name to filter the tags by.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("name") name: String? = null,
        @Parameter(
            description = "Comma-separated list of filters to apply to tag names.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Number of data series per page.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue(defaultValue = "20") @Positive @Max(100) size: Int,
    ): Map<String, Collection<String>> {
        return dataProvider.searchTagsAndValues(tenant, dataType, name, filter.asFilters(), size)
    }

    @Get("/")
    @Operation(
        summary = "Search data series",
        description = "Returns a list of data series that match the search parameters for the contextual tenant. Supports filtering and sorting.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Data series retrieved successfully."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_DATA_SERIES])
    @LogInput
    suspend fun searchDataSeries(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Campaign key to filter the data-series by.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("campaign") campaignKey: String? = null,
        @Parameter(
            description = "Sorting property and order, e.g., `name:DESC`.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("sort", defaultValue = "displayName:asc") sort: String,
        @Parameter(hidden = true) authentication: Authentication,
        @Parameter(
            description = "Comma-separated filters to apply to data series fields.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("filter", defaultValue = "") filter: String,
        @Parameter(
            description = "Zero-based page index to retrieve.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @QueryValue("page", defaultValue = "0") @PositiveOrZero page: Int,
        @Parameter(
            description = "Number of data series per page.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @QueryValue("size", defaultValue = "20") @Positive @Max(100) size: Int,
    ): Page<DataSeries> {
        return dataSeriesService.searchDataSeries(
            tenant,
            authentication.name,
            campaignKey = campaignKey,
            filter.asFilters(),
            sort.takeUnless(String::isNullOrBlank),
            page,
            size
        )
    }

    @Patch("/refresh-all")
    @Operation(
        summary = "Refresh prepared queries",
        description = "Updates the prepared queries for all data series.",
        responses = [
            ApiResponse(responseCode = "202", description = "Data series queries refreshed successfully."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(Permissions.WRITE_DATA_SERIES_PREPARED_QUERIES)
    @Timed("data-series-refresh")
    @Status(HttpStatus.ACCEPTED)
    suspend fun refresh(
        @Parameter(hidden = true) authentication: Authentication
    ) {
        dataSeriesService.refresh()
    }

}