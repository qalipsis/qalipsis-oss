package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesRecord
import jakarta.inject.Singleton

/**
 * Implementation of [TimeSeriesDataQueryService] used when no bean of [TimeSeriesDataProvider] can be found.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(missingBeans = [TimeSeriesDataProvider::class])
internal class NoOpTimeSeriesDataQueryServiceImpl : TimeSeriesDataQueryService {

    override suspend fun render(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: AggregationQueryExecutionRequest
    ): Map<String, List<TimeSeriesAggregationResult>> = emptyMap()

    override suspend fun search(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionContext: DataRetrievalQueryExecutionRequest
    ): Map<String, Page<TimeSeriesRecord>> = emptyMap()
}