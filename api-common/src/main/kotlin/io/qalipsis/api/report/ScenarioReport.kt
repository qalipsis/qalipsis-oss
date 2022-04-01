package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.ScenarioName
import java.time.Instant

/**
 * Report of a test campaign for a given scenario.
 *
 * @author Eric Jessé
 */
data class ScenarioReport(
    val campaignName: CampaignName,
    val scenarioName: ScenarioName,
    val start: Instant,
    val end: Instant,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val status: ExecutionStatus,
    val messages: List<ReportMessage>
)
