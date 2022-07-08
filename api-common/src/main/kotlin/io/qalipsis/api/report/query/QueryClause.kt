package io.qalipsis.api.report.query

import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Clause to search data in a [QueryDescription].
 *
 * @author Eric Jessé
 */
@Introspected
data class QueryClause(
    @field:NotBlank
    @field:Size(min = 1, max = 60)
    val name: String,
    val operator: QueryClauseOperator = QueryClauseOperator.IS,
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val value: String
)