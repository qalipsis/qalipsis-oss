package io.qalipsis.core.head.report

import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesRecord

/**
 * Service in charge of executing prepared queries of [io.qalipsis.core.head.model.DataSeries].
 *
 * @author Eric Jessé
 */
internal interface TimeSeriesDataQueryService {

    /**
     * Render the aggregated data to draw diagrams.
     *
     * @param tenant the tenant owning the time-series data
     * @param dataSeriesReferences references of data-series to use for the aggregation
     * @param queryExecutionRequest context of execution of the query
     *
     * @return a map of lists of [TimeSeriesAggregationResult], keyed by the corresponding item of [dataSeriesReferences].
     */
    suspend fun render(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: AggregationQueryExecutionRequest
    ): Map<String, List<TimeSeriesAggregationResult>>

    /**
     * Retrieve the time-series records to draw diagrams.
     *
     * @param tenant the tenant owning the time-series data
     * @param dataSeriesReferences references of data-series to use for the aggregation
     * @param queryExecutionRequest context of execution of the query
     *
     * @return a map of pages of [TimeSeriesRecord], keyed by the corresponding item of [dataSeriesReferences].
     */
    suspend fun search(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: DataRetrievalQueryExecutionRequest
    ): Map<String, Page<out TimeSeriesRecord>>
}