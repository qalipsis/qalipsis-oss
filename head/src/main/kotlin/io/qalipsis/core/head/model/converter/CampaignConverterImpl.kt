package io.qalipsis.core.head.model.converter

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignReport
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.ReportMessage
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioReport
import io.qalipsis.core.head.model.ScenarioRequest
import jakarta.inject.Singleton

/**
 * Convertor for different formats around the campaigns.
 *
 * @author Palina Bril
 */
@Singleton
internal class CampaignConverterImpl(
    private val userRepository: UserRepository,
    private val scenarioRepository: CampaignScenarioRepository,
    private val idGenerator: IdGenerator
) : CampaignConverter {

    override suspend fun convertRequest(
        tenant: String,
        campaign: CampaignRequest
    ): CampaignConfiguration {
        return CampaignConfiguration(
            tenant = tenant,
            key = idGenerator.short(),
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    override suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign {
        return Campaign(
            version = campaignEntity.version,
            key = campaignEntity.key,
            name = campaignEntity.name,
            speedFactor = campaignEntity.speedFactor,
            start = campaignEntity.start,
            end = campaignEntity.end,
            result = campaignEntity.result,
            configurerName = userRepository.findUsernameById(campaignEntity.configurer),
            scenarios = scenarioRepository.findByCampaignId(campaignEntity.id).map { scenarioEntity ->
                Scenario(
                    scenarioEntity.version,
                    scenarioEntity.name,
                    scenarioEntity.minionsCount
                )
            }
        )
    }

    override suspend fun convertReport(campaignReport: io.qalipsis.api.report.CampaignReport): CampaignReport {
        return CampaignReport(
            campaignKey = campaignReport.campaignKey,
            start = campaignReport.start,
            end = campaignReport.end,
            startedMinions = campaignReport.startedMinions,
            completedMinions = campaignReport.completedMinions,
            successfulExecutions = campaignReport.successfulExecutions,
            failedExecutions = campaignReport.failedExecutions,
            status = campaignReport.status,
            scenariosReports = convertScenarioReport(campaignReport.scenariosReports)
        )
    }

    private fun convertScenarioReport(scenariosReports: List<io.qalipsis.api.report.ScenarioReport>): List<ScenarioReport> {
        return scenariosReports.map {
            ScenarioReport(
                campaignKey = it.campaignKey,
                scenarioName = it.scenarioName,
                start = it.start,
                end = it.end,
                startedMinions = it.startedMinions,
                completedMinions = it.completedMinions,
                successfulExecutions = it.successfulExecutions,
                failedExecutions = it.failedExecutions,
                status = it.status,
                messages = convertReportMessages(it.messages)
            )
        }.toList()
    }

    private fun convertReportMessages(reportMessages: List<io.qalipsis.api.report.ReportMessage>): List<ReportMessage> {
        return reportMessages.map {
            ReportMessage(
                stepName = it.stepName,
                messageId = it.messageId,
                severity = it.severity,
                message = it.message
            )
        }.toList()
    }

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.map { it.key to ScenarioConfiguration(it.value.minionsCount) }.toMap()
    }
}