package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ScenarioReport
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
    private val campaignReportRepository: CampaignReportRepository,
) : CampaignReportProvider {

    override suspend fun retrieveCampaignReport(tenant: String, campaignKey: CampaignKey): CampaignReport {
        val campaignEntity = campaignRepository.findByKey(tenant = tenant, campaignKey)
        val campaignReportEntity = campaignReportRepository.findByCampaignId(campaignEntity.id)
        return CampaignReport(
            campaignKey = campaignKey,
            start = campaignEntity.start,
            end = campaignEntity.end,
            startedMinions = campaignReportEntity.startedMinions,
            completedMinions = campaignReportEntity.completedMinions,
            successfulExecutions = campaignReportEntity.successfulExecutions,
            failedExecutions = campaignReportEntity.failedExecutions,
            status = campaignEntity.result!!,
            scenariosReports = mapScenarioReport(campaignKey, campaignReportEntity.scenariosReports),
        )
    }

    private fun mapScenarioReport(
        campaignKey: CampaignKey,
        scenarioReportEntities: List<ScenarioReportEntity>
    ): List<ScenarioReport> {
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
                messages = mapScenarioReportMessageEntity(it.messages)
            )
        }
    }

    private fun mapScenarioReportMessageEntity(scenarioReportMessageEntities: List<ScenarioReportMessageEntity>): List<ReportMessage> {
        return scenarioReportMessageEntities.map {
            ReportMessage(
                stepName = it.stepName,
                messageId = it.messageId,
                severity = it.severity,
                message = it.messageId
            )
        }
    }
}