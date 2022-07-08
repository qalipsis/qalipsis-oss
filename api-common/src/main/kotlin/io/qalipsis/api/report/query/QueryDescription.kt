package io.qalipsis.api.report.query

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.Valid

/**
 * Description of a query to be executed on events or meters.
 *
 * @author Eric Jessé
 */
@Introspected
data class QueryDescription(
    val filters: Collection<@Valid QueryClause> = emptySet(),
    val fieldName: String? = null,
    val aggregationOperation: QueryAggregationOperator = QueryAggregationOperator.COUNT,
    @field:PositiveDuration
    val timeframeUnit: Duration?
)
