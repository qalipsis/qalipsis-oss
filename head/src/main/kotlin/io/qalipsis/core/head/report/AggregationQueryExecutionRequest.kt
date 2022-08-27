package io.qalipsis.core.head.report

import io.micronaut.core.annotation.Introspected
import java.time.Duration
import java.time.Instant

/**
 * Description of the context and additional clauses for the execution of prepared aggregation queries
 * for time-series data.
 *
 * @property campaignsReferences references of all the campaigns that generated the data
 * @property scenariosNames names of all the scenarios that generated the data, defaults to empty for all the scenarios of the selected campaigns
 * @property from start of the data to aggregate, defaults to null for all the data of the selected campaigns until [until]
 * @property until end of the data to aggregate, defaults to null for all the data of the selected campaigns since [from]
 * @property aggregationTimeframe duration of the time-buckets to perform the aggregations, defaults to null for the one set in each pre-configured query
 *
 * @author Eric Jessé
 */
@Introspected
data class AggregationQueryExecutionRequest(
    val campaignsReferences: Set<String>,
    val scenariosNames: Set<String> = emptySet(),
    val from: Instant? = null,
    val until: Instant? = null,
    val aggregationTimeframe: Duration? = null
)
