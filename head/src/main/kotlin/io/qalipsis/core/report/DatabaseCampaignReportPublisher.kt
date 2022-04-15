package io.qalipsis.core.report

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList

/**
 * Service in charge of persisting a report of a campaign into the database.
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.VOLATILE]),
    Requires(property = "report.export.database.enabled", defaultValue = "true", value = "false")
)
internal class DatabaseCampaignReportPublisher(
    private val campaignRepository: CampaignRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportRepository: ScenarioReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository
) : CampaignReportPublisher {

    override suspend fun publish(campaign: CampaignConfiguration, report: CampaignReport) {
        val campaignReportEntity = saveCampaignReport(campaign.tenant, report)
        val scenarioReportEntitiesToSave = report.scenariosReports.map {
            mapToScenarioReportEntity(it, campaignReportEntity.id)
        }.toList()
        val scenarioReportEntitiesSaved = scenarioReportRepository.saveAll(scenarioReportEntitiesToSave).toList()
        val scenariosIdsByName = scenarioReportEntitiesSaved.associate { it.name to it.id }
        val scenarioReportMessageEntitiesToSave = mutableListOf<ScenarioReportMessageEntity>()
        report.scenariosReports.forEach { scenarioReport ->
            scenarioReportMessageEntitiesToSave.addAll(
                mapMessagesToScenarioReportMessageEntities(
                    scenarioReport.messages,
                    scenariosIdsByName[scenarioReport.scenarioName]!!
                )
            )
        }
        scenarioReportMessageRepository.saveAll(scenarioReportMessageEntitiesToSave).toList()
    }

    private suspend fun saveCampaignReport(tenant: String, campaignReport: CampaignReport): CampaignReportEntity {
        return campaignReportRepository.save(
            CampaignReportEntity(
                campaignRepository.findIdByName(tenant, campaignReport.campaignName),
                campaignReport.startedMinions,
                campaignReport.completedMinions,
                campaignReport.successfulExecutions,
                campaignReport.failedExecutions
            )
        )
    }

    private fun mapToScenarioReportEntity(
        scenarioReport: ScenarioReport,
        campaignReportEntityId: Long
    ): ScenarioReportEntity {
        return ScenarioReportEntity(
            scenarioReport.scenarioName,
            campaignReportEntityId,
            scenarioReport.start,
            scenarioReport.end,
            scenarioReport.startedMinions,
            scenarioReport.completedMinions,
            scenarioReport.successfulExecutions,
            scenarioReport.failedExecutions,
            scenarioReport.status
        )
    }

    private fun mapMessagesToScenarioReportMessageEntities(
        reportMessages: List<ReportMessage>,
        scenarioReportEntityId: Long
    ): List<ScenarioReportMessageEntity> {
        return reportMessages.map {
            ScenarioReportMessageEntity(
                scenarioReportEntityId,
                it.stepName,
                it.messageId.toString(),
                it.severity,
                it.message
            )
        }
    }
}