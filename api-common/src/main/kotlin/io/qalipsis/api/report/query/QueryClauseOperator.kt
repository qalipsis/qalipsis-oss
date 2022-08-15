package io.qalipsis.api.report.query

import io.micronaut.core.annotation.Introspected

/**
 * Operator to apply on a clause for a query.
 *
 * @author Eric Jessé
 */
@Introspected
enum class QueryClauseOperator {
    IS, IS_NOT, IS_IN, IS_NOT_IN, IS_LIKE, IS_NOT_LIKE, IS_GREATER_THAN, IS_LOWER_THAN, IS_GREATER_OR_EQUAL_TO, IS_LOWER_OR_EQUAL_TO
}