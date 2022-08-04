package io.qalipsis.core.head.report

import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.query.QueryDescription
import javax.validation.constraints.Max
import javax.validation.constraints.Positive

internal interface DataProvider {

    /**
     * Searches names of events in the tenant, matching the filters if specified.
     *
     * @param tenant the reference of the tenant owning the data
     * @param dataType the type of data to inspect
     * @param filters the different filters (potentially with wildcard *) the names should match
     * @param size the maximum count of results to return
     */
    suspend fun searchNames(
        tenant: String,
        dataType: DataType,
        filters: Collection<String>,
        @Positive @Max(100) size: Int
    ): Collection<String>

    /**
     * List all the fields that can be used for aggregation of data on events.
     *
     * @param tenant the reference of the tenant owning the data
     * @param dataType the type of data to inspect
     */
    suspend fun listFields(tenant: String, dataType: DataType): Collection<DataField>

    /**
     * Searches tags matching the potential filters and provide also values.
     *
     * @param tenant the reference of the tenant owning the data
     * @param dataType the type of data to inspect
     * @param filters the different filters (potentially with wildcard *) the tags names should match
     * @param size the maximum count of results of tags names and values for each name
     */
    suspend fun searchTagsAndValues(
        tenant: String,
        dataType: DataType,
        filters: Collection<String>,
        @Positive @Max(100) size: Int
    ): Map<String, Collection<String>>

    /**
     * Prepares the query on events and returns it wrapped into a JSON object containing potential additional metadata.
     */
    suspend fun createQuery(tenant: String, dataType: DataType, query: QueryDescription): String

}