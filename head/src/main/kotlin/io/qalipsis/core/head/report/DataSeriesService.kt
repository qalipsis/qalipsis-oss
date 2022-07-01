package io.qalipsis.core.head.report

import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesPatch

/**
 * Service to proceed (get, save, update, delete) the storage in database of the data series.
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
}