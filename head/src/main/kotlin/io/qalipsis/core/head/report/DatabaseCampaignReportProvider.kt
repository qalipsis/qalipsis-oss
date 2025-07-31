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

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.ExecutionStatus.IN_PROGRESS
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import io.qalipsis.core.head.jdbc.repository.CampaignReportRepository
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.converter.CampaignConverter
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignReportProvider] interface
 *
 * @author Francisca Eze
 */
@Singleton
@Primary
@Requires(beans = [DatabaseCampaignReportPublisher::class])
class DatabaseCampaignReportProvider(
    private val campaignRepository: CampaignRepository,
    private val campaignConverter: CampaignConverter,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignReportRepository: CampaignReportRepository,
    private val scenarioReportMessageRepository: ScenarioReportMessageRepository
) : CampaignReportProvider {


    override suspend fun retrieveCampaignsReports(
        tenant: String,
        campaignKeys: Collection<CampaignKey>
    ): Collection<CampaignExecutionDetails> {
        val campaignEntities = campaignRepository.findByTenantAndKeys(tenant, campaignKeys)
        return campaignEntities.map { campaignEntity ->
            val campaign = campaignConverter.convertToModel(campaignEntity)
            // When working with the database, the report is only available once the campaign is complete.
            val campaignReport = campaignReportRepository.findByCampaignId(campaignEntity.id)
            CampaignExecutionDetails(
                version = campaign.version,
                key = campaign.key,
                creation = campaign.creation,
                name = campaign.name,
                speedFactor = campaign.speedFactor,
                scheduledMinions = campaign.scheduledMinions,
                softTimeout = campaign.softTimeout,
                hardTimeout = campaign.hardTimeout,
                start = campaign.start,
                end = campaign.end,
                status = campaignReport?.status ?: campaign.status,
                failureReason = campaign.failureReason,
                configurerName = campaign.configurerName,
                aborterName = campaign.aborterName,
                scenarios = campaign.scenarios,
                zones = campaign.zones,
                startedMinions = campaignReport?.startedMinions,
                completedMinions = campaignReport?.completedMinions,
                successfulExecutions = campaignReport?.successfulExecutions,
                failedExecutions = campaignReport?.failedExecutions,
                scenariosReports = campaignReport?.scenariosReports?.let { mapScenarioReport(it) }
                    ?: ongoingScenariosDetails(campaignEntity.id)
            )
        }
    }


    private suspend fun mapScenarioReport(scenarioReportEntities: List<ScenarioReportEntity>): List<ScenarioExecutionDetails> {
        val messages =
            scenarioReportMessageRepository.findByScenarioReportIdInOrderById(scenarioReportEntities.map { it.id })
                .groupBy { it.scenarioReportId }
        return scenarioReportEntities.map {
            ScenarioExecutionDetails(
                id = it.name,
                name = it.name,
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

    /**
     * Provides default values for the scenarios when the campaign is ongoing.
     */
    private suspend fun ongoingScenariosDetails(campaignId: Long) =
        campaignScenarioRepository.findByCampaignId(campaignId).map { scenario ->
            ScenarioExecutionDetails(
                id = scenario.name,
                name = scenario.name,
                start = scenario.start,
                end = null,
                startedMinions = null,
                completedMinions = null,
                successfulExecutions = null,
                failedExecutions = null,
                status = if (scenario.start == null) QUEUED else IN_PROGRESS,
                messages = emptyList()
            )
        }
}