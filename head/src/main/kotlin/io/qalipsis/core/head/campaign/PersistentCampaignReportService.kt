package io.qalipsis.core.head.campaign

import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList

@Singleton
internal class PersistentCampaignReportService(
    private val campaignRepository: CampaignRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportRepository: ScenarioReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository
) : CampaignReportService {

    /**
     * Saves CampaignReport, ScenarioReport and ScenarioReportMessage entities
     */
    override suspend fun save(campaignReport: CampaignReport) {
        val campaignReportEntity = saveCampaignReport(campaignReport)
        val scenarioReportEntitiesToSave = campaignReport.scenariosReports.map {
            mapToScenarioReportEntity(it, campaignReportEntity.id)
        }.toList()
        val scenarioReportEntitiesSaved = scenarioReportRepository.saveAll(scenarioReportEntitiesToSave).toList()
        val scenariosIdsByName = scenarioReportEntitiesSaved.associate { it.name to it.id }
        val scenarioReportMessageEntitiesToSave = mutableListOf<ScenarioReportMessageEntity>()
        campaignReport.scenariosReports.forEach { scenarioReport ->
            scenarioReportMessageEntitiesToSave.addAll(
                mapMessagesToScenarioReportMessageEntities(
                    scenarioReport.messages,
                    scenariosIdsByName[scenarioReport.scenarioId]!!
                )
            )
        }
        scenarioReportMessageRepository.saveAll(scenarioReportMessageEntitiesToSave).toList()
    }

    private suspend fun saveCampaignReport(
        campaignReport: CampaignReport
    ): CampaignReportEntity {
        return campaignReportRepository.save(
            CampaignReportEntity(
                campaignRepository.findIdByName(campaignReport.campaignId),
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
            scenarioReport.scenarioId,
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
                it.stepId,
                it.messageId.toString(),
                it.severity,
                it.message
            )
        }
    }
}