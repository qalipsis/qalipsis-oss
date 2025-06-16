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
import java.time.Duration
import java.time.Instant

/**
 * Description of the context and additional clauses for the execution of prepared query for time-series data.
 *
 * @property tenant the reference of the tenant owning the data
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
    val tenant: String,
    val campaignsReferences: Set<String>,
    val scenariosNames: Set<String> = emptySet(),
    val from: Instant,
    val until: Instant,
    val aggregationTimeframe: Duration,
    val page: Int = 0,
    val size: Int = 500,
    val sort: String? = null
)
