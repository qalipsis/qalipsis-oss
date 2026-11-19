/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.report

import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.api.report.TimeSeriesValues

/**
 * Service in charge of executing prepared queries of [io.qalipsis.core.head.model.DataSeries].
 *
 * @author Eric Jessé
 */
interface TimeSeriesDataQueryService {

    /**
     * Render the aggregated data to draw diagrams.
     *
     * @param tenant the tenant owning the time-series data
     * @param dataSeriesReferences references of data-series to use for the aggregation
     * @param queryExecutionRequest context of execution of the query
     * @param username when set, only the data series readable by this user are used
     *
     * @return a map of [TimeSeriesValues] keyed by the corresponding item of [dataSeriesReferences].
     */
    suspend fun render(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: AggregationQueryExecutionRequest,
        username: String? = null
    ): Map<String, TimeSeriesValues>

    /**
     * Retrieve the time-series records to draw diagrams.
     *
     * @param tenant the tenant owning the time-series data
     * @param dataSeriesReferences references of data-series to use for the aggregation
     * @param queryExecutionRequest context of execution of the query
     * @param username when set, only the data series readable by this user are used
     *
     * @return a map of pages of [TimeSeriesRecord], keyed by the corresponding item of [dataSeriesReferences].
     */
    suspend fun search(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: DataRetrievalQueryExecutionRequest,
        username: String? = null
    ): Map<String, Page<out TimeSeriesRecord>>
}