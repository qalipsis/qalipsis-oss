package io.qalipsis.core.head.report

import io.qalipsis.core.head.model.Report
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest

/**
 * Service to proceed (get, save, update, delete) the storage in database of the report.
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

}