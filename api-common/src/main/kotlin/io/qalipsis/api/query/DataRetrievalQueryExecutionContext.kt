package io.qalipsis.api.query

import io.micronaut.core.annotation.Introspected
import java.time.Duration
import java.time.Instant

/**
 * Description of the context and additional clauses for the execution of prepared query for time-series data.
 *
 * @property campaignsReferences references of all the campaigns that generated the data
 * @property scenariosNames names of all the scenarios that generated the data, defaults to empty for all the scenarios of the selected campaigns
 * @property from start of the data to retrieve, defaults to null for all the data of the selected campaigns until [until]
 * @property until end of the data to retrieve, defaults to null for all the data of the selected campaigns since [from]
 * @property aggregationTimeframe duration of the time-buckets to perform the corresponding aggregations, in order to select rounded ranges that match the aggregation buckets
 * @property page 0-based index of the page of records to retrieve
 * @property size maximum count of records to retrieve in the current page, defaults to 500
 * @property sort order for the data to retrieve (asc, desc), defaults to the implementation of the [io.qalipsis.api.report.TimeSeriesDataProvider].
 *
 * @author Eric Jessé
 */
@Introspected
data class DataRetrievalQueryExecutionContext(
    val campaignsReferences: Set<String>,
    val scenariosNames: Set<String> = emptySet(),
    val from: Instant,
    val until: Instant,
    val aggregationTimeframe: Duration,
    val page: Int = 0,
    val size: Int = 500,
    val sort: String? = null
)
