package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignId
import java.time.Instant

/**
 * Aggregated report of all the scenarios of a campaign.
 *
 * @author Eric Jessé
 */
data class CampaignReport(
    val campaignId: CampaignId,
    val start: Instant,
    val end: Instant?,
    val startedMinions: Int = 0,
    val completedMinions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val status: ExecutionStatus,
    val scenariosReports: List<ScenarioReport> = emptyList()
)
