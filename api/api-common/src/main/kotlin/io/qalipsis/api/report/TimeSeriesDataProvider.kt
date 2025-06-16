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

package io.qalipsis.api.report

import io.micronaut.context.annotation.Requires
import io.micronaut.validation.Validated
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page

/**
 * Interface of service to execute prepared queries from time-series sources.
 *
 * @author Eric Jessé
 */
@Requires(env = ["standalone", "head"])
@Validated
interface TimeSeriesDataProvider {

    /**
     * Execute the aggregations statement of the prepared queries passed as [preparedQueries].
     *
     * @param preparedQueries the prepared queries to execute, keyed by an identifier to map the results, generally the data series reference.
     * @param context additional clauses and information to specialize the execution of the queries
     *
     * @return a map of lists of [TimeSeriesAggregationResult] keyed by the corresponding keys in [preparedQueries]
     *
     * @see [EventMetadataProvider.createQuery]
     * @see [MeterMetadataProvider.createQuery]
     */
    suspend fun executeAggregations(
        preparedQueries: Map<String, String>,
        context: AggregationQueryExecutionContext
    ): Map<String, List<TimeSeriesAggregationResult>>

    /**
     * Execute the data retrieval statement of the prepared queries passed as [preparedQueries].
     *
     * @param preparedQueries the prepared queries to execute, keyed by an identifier to map the results, generally the data series reference.
     * @param context additional clauses and information to specialize the execution of the queries
     *
     * @return a map of sorted lists of [TimeSeriesRecord] keyed by the corresponding keys in [preparedQueries]
     *
     * @see [EventMetadataProvider.createQuery]
     * @see [MeterMetadataProvider.createQuery]
     *
     */
    suspend fun retrieveRecords(
        preparedQueries: Map<String, String>,
        context: DataRetrievalQueryExecutionContext
    ): Map<String, Page<TimeSeriesRecord>>

    /**
     * Retrieve the disk space used in a tenant, in bytes.
     *
     * @param tenant the reference of the tenant.
     */
    suspend fun retrieveUsedStorage(tenant: String): Long
}