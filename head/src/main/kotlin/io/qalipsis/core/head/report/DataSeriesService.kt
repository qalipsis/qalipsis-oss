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

import io.qalipsis.api.query.Page
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesCreationRequest
import io.qalipsis.core.head.model.DataSeriesPatch

/**
 * Service to proceed (get, save, update, delete, search) the storage in database of the data series.
 *
 * @author Palina Bril
 */
internal interface DataSeriesService {

    /**
     * Creates new the data series.
     */
    suspend fun create(tenant: String, creator: String, dataSeries: DataSeriesCreationRequest): DataSeries

    /**
     * Returns data series.
     */
    suspend fun get(tenant: String, username: String, reference: String): DataSeries

    /**
     *  Applies the different patches to [DataSeries] and persists those changes.
     */
    suspend fun update(
        tenant: String,
        username: String,
        reference: String,
        patches: Collection<DataSeriesPatch>
    ): DataSeries

    /**
     * Delete the data series.
     */
    suspend fun delete(tenant: String, username: String, reference: String)

    /**
     * Search data series in the specified tenant
     *
     * @param tenant the reference of the tenant owning the data
     * @param username username of the currently authenticated user
     * @param filters the different filters (potentially with wildcard *) the series should match
     * @param sort the sorting option which defaults to display name
     * @param size the maximum count of results of tags names and values for each name
     * @param page the page to start searching from
     */
    suspend fun searchDataSeries(
        tenant: String,
        username: String,
        filters: Collection<String>,
        sort: String?,
        page: Int,
        size: Int
    ): Page<DataSeries>

}