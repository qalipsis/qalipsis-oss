package io.qalipsis.core.head.report

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import java.time.Instant

/**
 * Reporting state of the execution of a scenario during or after a campaign.
 *
 * @author Eric Jessé
 */
interface ScenarioReportingExecutionState {

    val scenarioName: ScenarioName

    val start: Instant

    val startedMinions: Int

    val completedMinions: Int

    val successfulStepExecutions: Int

    val failedStepExecutions: Int

    val end: Instant?

    val abort: Instant?

    val status: ExecutionStatus?

    val messages: Map<Any, ReportMessage>

    fun toReport(campaignName: String): ScenarioReport {
        val endTimestamp = end
        val abortTimestamp = abort
        val scenarioEnd = if (abortTimestamp != null && endTimestamp != null) {
            endTimestamp.coerceAtMost(abortTimestamp)
        } else {
            endTimestamp ?: abortTimestamp ?: Instant.now()
        }
        val actualStatus = status ?: when {
            messages.values.any { it.severity == ReportMessageSeverity.ABORT } -> ExecutionStatus.ABORTED
            messages.values.any { it.severity == ReportMessageSeverity.ERROR } -> ExecutionStatus.FAILED
            messages.values.any { it.severity == ReportMessageSeverity.WARN } -> ExecutionStatus.WARNING
            else -> ExecutionStatus.SUCCESSFUL
        }

        return ScenarioReport(
            campaignName,
            scenarioName,
            start,
            scenarioEnd,
            startedMinions,
            completedMinions,
            successfulStepExecutions,
            failedStepExecutions,
            actualStatus,
            messages.values.toList()
        )
    }
}