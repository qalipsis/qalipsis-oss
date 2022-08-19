package io.qalipsis.core.head.report

import io.qalipsis.core.head.model.Page
import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest

/**
 * Service to proceed (get, save, update, delete, search) the storage in database of the report.
 *
 * @author Joël Valère
 */
internal interface ReportService {

    /**
     * Creates a new report.
     */
    suspend fun create(tenant: String, creator: String, reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest): Report

    /**
     * Returns a fully described report.
     */
    suspend fun get(tenant: String, username: String, reference: String): Report

    /**
     * Updates the report.
     */
    suspend fun update(tenant: String, username: String, reference: String, reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest): Report

    /**
     * Deletes the report.
     */
    suspend fun delete(tenant: String, username: String, reference: String)

    /**
     * Search reports in the specified tenant
     *
     * @param tenant the reference of the tenant owning the report
     * @param username username of the currently authenticated user
     * @param filters the different filters (potentially with wildcard *) the report should match
     * @param sort the sorting option which defaults to display name
     * @param size the maximum count of results in a page
     * @param page the 0-based index of the page to fetch
     */
    suspend fun search(tenant: String, username: String, filters: Collection<String>, sort: String?, page: Int, size: Int): Page<Report>
}