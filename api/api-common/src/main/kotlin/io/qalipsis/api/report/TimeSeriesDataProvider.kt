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
    ): Map<String, TimeSeriesValues>

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
     * Retrieves all meters recorded for the specified campaign, optionally filtered by scenario names.
     *
     * The result is intended for use in execution reports (HTML and similar) where all meters produced
     * during a campaign need to be listed without a pre-defined data-series query.
     *
     * @param tenant the reference of the tenant owning the data.
     * @param campaignKeys unique keys of the campaigns to retrieve meters for; must not be empty.
     * @param scenarioNames names of the scenarios to restrict the result to; empty means all scenarios.
     *
     * @return all [TimeSeriesMeter]s recorded during the campaigns, sorted by name then by timestamp.
     */
    suspend fun retrieveCampaignMeters(
        tenant: String,
        campaignKeys: Collection<String>,
        scenarioNames: Collection<String> = emptyList()
    ): List<TimeSeriesMeter>

    /**
     * Retrieve the disk space used in a tenant, in bytes.
     *
     * @param tenant the reference of the tenant.
     */
    suspend fun retrieveUsedStorage(tenant: String): Long
}