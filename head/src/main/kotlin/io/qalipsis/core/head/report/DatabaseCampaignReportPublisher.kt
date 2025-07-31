/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.annotations.LogInput
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
import org.slf4j.event.Level

/**
 * Service in charge of persisting a report of a campaign into the database.
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT]),
    Requires(
        property = "report.export.database.enabled",
        notEquals = StringUtils.FALSE,
        defaultValue = StringUtils.TRUE
    )
)
class DatabaseCampaignReportPublisher(
    private val campaignRepository: CampaignRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportRepository: ScenarioReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository
) : CampaignReportPublisher {

    @LogInput(Level.DEBUG)
    override suspend fun publish(campaignKey: CampaignKey, report: CampaignReport) {
        val campaignReportEntity = saveCampaignReport(report)
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
        if (scenarioReportMessageEntitiesToSave.isNotEmpty()) {
            scenarioReportMessageRepository.saveAll(scenarioReportMessageEntitiesToSave).toList()
        }
    }

    private suspend fun saveCampaignReport(campaignReport: CampaignReport): CampaignReportEntity {
        val campaignEntity = campaignRepository.findByKey(campaignReport.campaignKey)
        // Synchronize the campaign details with the ones from the report.
        val updatedCampaignEntity = campaignRepository.update(
            campaignEntity.copy(
                result = campaignEntity.result?.takeIf { it in setOf(ExecutionStatus.ABORTED, ExecutionStatus.FAILED) }
                    ?: campaignReport.status,
                end = campaignEntity.end ?: campaignReport.end
            )
        )
        return campaignReportRepository.save(
            CampaignReportEntity(
                campaignId = campaignEntity.id,
                startedMinions = campaignReport.startedMinions!!,
                completedMinions = campaignReport.completedMinions!!,
                successfulExecutions = campaignReport.successfulExecutions!!,
                failedExecutions = campaignReport.failedExecutions!!,
                status = updatedCampaignEntity.result!!
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
            scenarioReport.start!!,
            scenarioReport.end!!,
            scenarioReport.startedMinions!!,
            scenarioReport.completedMinions!!,
            scenarioReport.successfulExecutions!!,
            scenarioReport.failedExecutions!!,
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
                it.messageId,
                it.severity,
                it.message
            )
        }
    }
}