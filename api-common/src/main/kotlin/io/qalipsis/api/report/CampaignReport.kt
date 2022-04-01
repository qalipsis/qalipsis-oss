package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignName
import java.time.Instant

/**
 * Aggregated report of all the scenarios of a campaign.
 *
 * @author Eric Jessé
 */
data class CampaignReport(
    val campaignName: CampaignName,
    val start: Instant,
    val end: Instant?,
    val startedMinions: Int = 0,
    val completedMinions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val status: ExecutionStatus,
    val scenariosReports: List<ScenarioReport> = emptyList()
)
