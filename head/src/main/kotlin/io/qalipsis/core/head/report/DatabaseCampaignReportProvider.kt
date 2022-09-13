package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignReportProvider] interface
 *
 * @author Francisca Eze
 */
@Singleton
@Primary
@Requires(beans = [DatabaseCampaignReportPublisher::class])
internal class DatabaseCampaignReportProvider(
    private val campaignRepository: CampaignRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository
) : CampaignReportProvider {

    override suspend fun retrieveCampaignReport(tenant: String, campaignKey: CampaignKey): CampaignReport {
        val campaignEntity = campaignRepository.findByKey(tenant = tenant, campaignKey)
        campaignReportRepository.findByCampaignId(campaignEntity.id)
        // When working with the database, the report is only available once the campaign is complete.
        return campaignReportRepository.findByCampaignId(campaignEntity.id).firstOrNull()?.let { reportEntity ->
            CampaignReport(
                campaignKey = campaignKey,
                start = campaignEntity.start,
                end = campaignEntity.end,
                scheduledMinions = campaignEntity.scheduledMinions,
                startedMinions = reportEntity.startedMinions,
                timeout = campaignEntity.timeout,
                hardTimeout = campaignEntity.hardTimeout,
                completedMinions = reportEntity.completedMinions,
                successfulExecutions = reportEntity.successfulExecutions,
                failedExecutions = reportEntity.failedExecutions,
                status = reportEntity.status,
                scenariosReports = mapScenarioReport(campaignKey, reportEntity.scenariosReports),
            )
        }
            ?: CampaignReport( // When the report is not yet available in the database, only the "known" current values are used.
                campaignKey = campaignKey,
                start = campaignEntity.start,
                end = campaignEntity.end,
                scheduledMinions = campaignEntity.scheduledMinions,
                startedMinions = null,
                timeout = campaignEntity.timeout,
                hardTimeout = campaignEntity.hardTimeout,
                completedMinions = null,
                successfulExecutions = null,
                failedExecutions = null,
                status = campaignEntity.result
                    ?: if (campaignEntity.start == null) ExecutionStatus.QUEUED else ExecutionStatus.IN_PROGRESS,
                scenariosReports = campaignScenarioRepository.findByCampaignId(campaignEntity.id).map { scenario ->
                    ScenarioReport(
                        campaignKey = campaignKey,
                        scenarioName = scenario.name,
                        start = scenario.start,
                        end = null,
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        status = if (scenario.start == null) ExecutionStatus.QUEUED else ExecutionStatus.IN_PROGRESS,
                        messages = emptyList()
                    )
                }
            )
    }

    private suspend fun mapScenarioReport(
        campaignKey: CampaignKey,
        scenarioReportEntities: List<ScenarioReportEntity>
    ): List<ScenarioReport> {
        val messages =
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(scenarioReportEntities.map { it.id })
                .groupBy { it.scenarioReportId }
        return scenarioReportEntities.map {
            ScenarioReport(
                campaignKey = campaignKey,
                scenarioName = it.name,
                start = it.start,
                end = it.end,
                startedMinions = it.startedMinions,
                completedMinions = it.completedMinions,
                successfulExecutions = it.successfulExecutions,
                failedExecutions = it.failedExecutions,
                status = it.status,
                messages = mapScenarioReportMessageEntity(messages[it.id].orEmpty())
            )
        }
    }

    private fun mapScenarioReportMessageEntity(scenarioReportMessageEntities: List<ScenarioReportMessageEntity>): List<ReportMessage> {
        return scenarioReportMessageEntities.map {
            ReportMessage(
                stepName = it.stepName,
                messageId = it.messageId,
                severity = it.severity,
                message = it.message
            )
        }
    }
}