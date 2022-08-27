package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.report.AggregationQueryExecutionRequest
import io.qalipsis.core.head.report.DataRetrievalQueryExecutionRequest
import io.qalipsis.core.head.report.TimeSeriesDataQueryService
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotation.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.time.Duration
import java.time.Instant
import javax.annotation.Nullable
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

@Validated
@Controller("/time-series")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Version("1.0")
internal class TimeSeriesController(private val timeSeriesDataQueryService: TimeSeriesDataQueryService) {

    @Get("/aggregate")
    @Operation(
        summary = "Aggregate time-series data over time",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Sorted aggregation results keyed by the data-series references"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_DATA_SERIES])
    suspend fun aggregate(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "References of the data-series describing the data to aggregate",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("series") dataSeriesReferences: Set<@NotBlank String>,
        @Parameter(
            description = "References of the campaigns to aggregate data on",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("campaigns") campaigns: Set<@NotBlank String>,
        @Parameter(
            description = "Names of the scenarios to aggregate data on, defaults to all the scenarios of the specified campaigns",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("scenarios") scenarios: Set<@NotBlank String>?,
        @Parameter(
            description = "Beginning of the aggregation window",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("from") from: Instant?,
        @Parameter(
            description = "End of the aggregation window",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("until") until: Instant?,
        @Parameter(
            description = "Size of the time-buckets to perform the aggregations",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @PositiveDuration @QueryValue("timeframe") timeframe: Duration?
    ): HttpResponse<Map<String, List<TimeSeriesAggregationResult>>> {
        require(from == null || until == null || from < until) { "The start instant of retrieval should be before the end, please check from and until arguments" }
        return HttpResponse.ok(
            timeSeriesDataQueryService.render(
                tenant, dataSeriesReferences, AggregationQueryExecutionRequest(
                    campaignsReferences = campaigns,
                    scenariosNames = scenarios.orEmpty(),
                    from = from,
                    until = until,
                    aggregationTimeframe = timeframe
                )
            )
        )
    }


    @Get("/search")
    @Operation(
        summary = "Searches time-series records in a window frame",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Time-based sorted time-series records keyed by the data-series references"
            ),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Secured(value = [Permissions.READ_DATA_SERIES])
    suspend fun searchRecords(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "References of the data-series describing the data to aggregate",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("series") dataSeriesReferences: Set<@NotBlank String>,
        @Parameter(
            description = "References of the campaigns to aggregate data on",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @NotEmpty @QueryValue("campaigns") campaigns: Set<@NotBlank String>,
        @Parameter(
            description = "Names of the scenarios to aggregate data on, defaults to all the scenarios of the specified campaigns",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("scenarios") scenarios: Set<@NotBlank String>?,
        @Parameter(
            description = "Beginning of the aggregation window",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("from") from: Instant,
        @Parameter(
            description = "End of the aggregation window",
            required = true,
            `in` = ParameterIn.QUERY
        )
        @Nullable @QueryValue("until") until: Instant,
        @Parameter(
            description = "Size of the time-buckets to perform the aggregations",
            required = false,
            `in` = ParameterIn.QUERY
        )
        @Nullable @PositiveDuration @QueryValue("timeframe") timeframe: Duration?,
        @Nullable @QueryValue("page", defaultValue = "0") @PositiveOrZero page: Int,
        @Parameter(
            description = "Size of the page to retrieve",
            required = false,
            `in` = ParameterIn.QUERY
        ) @Nullable @QueryValue("size", defaultValue = "500") @Positive @Max(10_000) size: Int

    ): HttpResponse<Map<String, Page<out TimeSeriesRecord>>> {
        require(from < until) { "The start instant of retrieval should be before the end, please check from and until arguments" }
        return HttpResponse.ok(
            timeSeriesDataQueryService.search(
                tenant, dataSeriesReferences, DataRetrievalQueryExecutionRequest(
                    campaignsReferences = campaigns,
                    scenariosNames = scenarios.orEmpty(),
                    from = from,
                    until = until,
                    aggregationTimeframe = timeframe,
                    page = page,
                    size = size
                )
            )
        )
    }
}