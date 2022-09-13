package io.qalipsis.core.head.report

import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ScenarioReport

/**
 * Consolidates a collection of [ScenarioReportingExecutionState] into a [CampaignReport].
 *
 * @author Eric Jessé
 */
internal fun Collection<ScenarioReport>.toCampaignReport(): CampaignReport {

    return CampaignReport(
        campaignKey = first().campaignKey,
        start = this.asSequence().mapNotNull { it.start }.minOrNull(),
        end = this.asSequence().mapNotNull { it.end }.maxOrNull(),
        scheduledMinions = null,
        startedMinions = this.asSequence().mapNotNull { it.startedMinions }.sum(),
        completedMinions = this.asSequence().mapNotNull { it.completedMinions }.sum(),
        successfulExecutions = this.asSequence().mapNotNull { it.successfulExecutions }.sum(),
        failedExecutions = this.asSequence().mapNotNull { it.failedExecutions }.sum(),
        status = when {
            any { it.status == ExecutionStatus.ABORTED } -> ExecutionStatus.ABORTED
            any { it.status == ExecutionStatus.FAILED } -> ExecutionStatus.FAILED
            any { it.status == ExecutionStatus.WARNING } -> ExecutionStatus.WARNING
            none { it.start != null } -> ExecutionStatus.QUEUED
            any { it.end == null } -> ExecutionStatus.QUEUED
            else -> ExecutionStatus.SUCCESSFUL
        },
        scenariosReports = toList()
    )
}