package io.qalipsis.core.head.report

import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ScenarioReport

/**
 * Consolidates a collection of [ScenarioReportingExecutionState] into a [CampaignReport].
 *
 * @author Eric Jessé
 */
internal fun Collection<out ScenarioReport>.toCampaignReport(): CampaignReport {

    return CampaignReport(
        campaignName = first().campaignName,
        start = minOf(ScenarioReport::start),
        end = maxOf { it.end },
        startedMinions = sumOf { it.startedMinions },
        completedMinions = sumOf { it.completedMinions },
        successfulExecutions = sumOf { it.successfulExecutions },
        failedExecutions = sumOf { it.failedExecutions },
        status = when {
            any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
            any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
            any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
            else -> ExecutionStatus.SUCCESSFUL
        },
        scenariosReports = toList()
    )
}