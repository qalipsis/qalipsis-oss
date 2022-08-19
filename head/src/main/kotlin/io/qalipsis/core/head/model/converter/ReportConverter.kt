package io.qalipsis.core.head.model.converter

import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.model.Report

/**
 * Interface of convertor for different reports formats of reports.
 *
 * @author Joël Valère
 */
internal interface ReportConverter {

    /**
     * Converts a [ReportEntity] instance to a [Report].
     */
    suspend fun convertToModel(reportEntity: ReportEntity): Report
}