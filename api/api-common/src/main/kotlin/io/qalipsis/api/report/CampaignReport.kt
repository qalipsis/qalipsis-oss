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
    val configuredMinionsCount: Int,
    val executedMinionsCount: Int,
    val stepsCount: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val status: ExecutionStatus,
    val scenariosReports: List<ScenarioReport>
)
