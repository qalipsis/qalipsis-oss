package io.qalipsis.core.head.model

/**
 * Status of report file generation.
 *
 * @property PENDING processing of report file generation task is yet to begin
 * @property PROCESSING report file generation task is ongoing
 * @property COMPLETED report file generation task completed successfully and report file was generated
 * @property FAILED report file generation failed
 *
 * @author Francisca Eze
 */
enum class ReportTaskStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}