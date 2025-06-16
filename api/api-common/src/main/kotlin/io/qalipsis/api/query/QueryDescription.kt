/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.query

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
    val timeframeUnit: Duration? = null
) {
    constructor(vararg filters: QueryClause) : this(filters = filters.toList())
    constructor(fieldName: String, vararg filters: QueryClause) : this(
        filters = filters.toList(),
        fieldName = fieldName
    )
}
