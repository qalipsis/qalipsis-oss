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

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.api.report.TimeSeriesValues
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.report.AggregationQueryExecutionRequest
import io.qalipsis.core.head.report.CampaignSummaryResult
import io.qalipsis.core.head.report.DataRetrievalQueryExecutionRequest
import io.qalipsis.core.head.report.TimeSeriesDataQueryService
import io.qalipsis.core.head.report.WidgetService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.Nullable
import java.time.Duration
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero


@Validated
@Controller("\${server.api-root}/time-series")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Version("1.0")
@Tag(name = "Analytics data")
class TimeSeriesController(
    private val timeSeriesDataQueryService: TimeSeriesDataQueryService,
    private val widgetService: WidgetService
) {

    @Get("/aggregate")
    @Operation(
        summary = "Aggregate time-series data",
        description = "Returns sorted aggregation results keyed by the data-series references.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Aggregation results retrieved successfully."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_TIME_SERIES])
    @LogInput
    suspend fun aggregate(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Data-series references to aggregate.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("series") dataSeriesReferences: Set<@NotBlank String>,
        @Parameter(
            description = "Campaign references to aggregate data on.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("campaigns") campaigns: Set<@NotBlank String>,
        @Parameter(
            description = "Scenario names to aggregate on; defaults to all scenarios of the specified campaigns.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("scenarios") scenarios: Set<@NotBlank String>?,
        @Parameter(
            description = "Zones to aggregate on; defaults to all zones of the specified campaigns.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("zones") zones: Set<@NotBlank String>?,
        @Parameter(
            description = "Start of the aggregation window.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("from") from: Instant?,
        @Parameter(
            description = "End of the aggregation window.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("until") until: Instant?,
        @Parameter(
            description = "Time-bucket size for aggregation.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @PositiveDuration @QueryValue("timeframe") timeframe: Duration?
    ): Map<String, TimeSeriesValues> {
        require(from == null || until == null || from < until) { "Start instant must be before end instant; check 'from' and 'until'." }
        return timeSeriesDataQueryService.render(
            tenant, dataSeriesReferences, AggregationQueryExecutionRequest(
                tenant = tenant,
                campaignsReferences = campaigns,
                scenariosNames = scenarios.orEmpty(),
                zones = zones.orEmpty(),
                from = from,
                until = until,
                aggregationTimeframe = timeframe
            )
        )
    }


    @Get("/search")
    @Operation(
        summary = "Search time-series records",
        description = "Returns time-series records in the specified window frame, sorted by time and keyed by the data-series references.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Time-series records retrieved successfully."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_TIME_SERIES])
    @LogInput
    suspend fun searchRecords(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Data-series references to search.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("series") dataSeriesReferences: Set<@NotBlank String>,
        @Parameter(
            description = "Campaign references to filter on.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("campaigns") campaigns: Set<@NotBlank String>,
        @Parameter(
            description = "Scenario names to filter on; defaults to all scenarios of the specified campaigns.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("scenarios") scenarios: Set<@NotBlank String>?,
        @Parameter(
            description = "Zones to aggregate on; defaults to all zones of the specified campaigns.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("zones") zones: Set<@NotBlank String>?,
        @Parameter(
            description = "Start of the search window frame.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("from") from: Instant,
        @Parameter(
            description = "End of the search window frame.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("until") until: Instant,
        @Parameter(
            description = "Time-bucket size for aggregation.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @PositiveDuration @QueryValue("timeframe") timeframe: Duration?,
        @Parameter(
            description = "Zero-based page index to retrieve.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("page", defaultValue = "0") @PositiveOrZero page: Int,
        @Parameter(
            description = "Number of time-series records per page.",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue("size", defaultValue = "500") @Positive @Max(10_000) size: Int

    ): Map<String, Page<out TimeSeriesRecord>> {
        require(from < until) { "Start instant must be before end instant; check 'from' and 'until'." }
        return timeSeriesDataQueryService.search(
            tenant, dataSeriesReferences, DataRetrievalQueryExecutionRequest(
                tenant = tenant,
                campaignsReferences = campaigns,
                scenariosNames = scenarios.orEmpty(),
                zones = zones.orEmpty(),
                from = from,
                until = until,
                aggregationTimeframe = timeframe,
                page = page,
                size = size
            )
        )
    }

    @Get("/summary/campaign-status")
    @Operation(
        summary = "Retrieve campaign summary data",
        description = "Returns aggregated campaign results over time, keyed by the campaign-series start references.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Campaign summary retrieved successfully."
            ),
            ApiResponse(responseCode = "400", description = "Invalid request parameters."),
            ApiResponse(responseCode = "401", description = "Missing permissions to execute the operation."),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_CAMPAIGN])
    @LogInput
    suspend fun fetchCampaignSummary(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant.",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Start of the aggregation window.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("from") from: Instant,
        @Parameter(
            description = "End of the aggregation window.",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("until") until: Instant?,
        @Parameter(
            description = "Difference between UTC and the user's time zone.",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("timeOffset") timeOffset: Float,
        @Parameter(
            description = "Time-bucket size for aggregation.",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        @PositiveDuration @QueryValue("timeframe", defaultValue = "PT24H") timeframe: Duration
    ): List<CampaignSummaryResult> {
        require(until == null || from < until) { "Start instant must be before end instant; check 'from' and 'until'." }
        return widgetService.aggregateCampaignResult(tenant, from, until, timeOffset, timeframe)
    }
}