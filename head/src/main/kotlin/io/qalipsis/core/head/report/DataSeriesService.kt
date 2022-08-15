package io.qalipsis.core.head.report

import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesPatch
import io.qalipsis.core.head.model.Page

/**
 * Service to proceed (get, save, update, delete, search) the storage in database of the data series.
 *
 * @author Palina Bril
 */
internal interface DataSeriesService {

    /**
     * Creates new the data series.
     */
    suspend fun create(creator: String, tenant: String, dataSeries: DataSeries): DataSeries

    /**
     * Returns data series.
     */
    suspend fun get(username: String, tenant: String, reference: String): DataSeries

    /**
     *  Applies the different patches to [DataSeries] and persists those changes.
     */
    suspend fun update(
        username: String,
        tenant: String,
        reference: String,
        patches: Collection<DataSeriesPatch>
    ): DataSeries

    /**
     * Delete the data series.
     */
    suspend fun delete(username: String, tenant: String, reference: String)

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