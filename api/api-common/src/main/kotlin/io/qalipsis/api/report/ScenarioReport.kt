package io.qalipsis.api.report

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import java.time.Instant

/**
 * Report of a test campaign for a given scenario.
 *
 * @author Eric Jessé
 */
data class ScenarioReport(
    val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val start: Instant,
    val end: Instant?,
    val configuredMinionsCount: Int,
    val executedMinionsCount: Int,
    val stepsCount: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val status: ExecutionStatus,
    val messages: List<ReportMessage>
)
