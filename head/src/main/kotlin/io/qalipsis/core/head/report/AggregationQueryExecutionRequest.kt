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
