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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportMessageRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioReportRepository
import io.qalipsis.core.head.model.ReportTaskStatus
import jakarta.inject.Singleton
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Handles the functionalities of report task generation.
 *
 * @author Francisca Eze
 */
@Singleton
class ReportGenerator(
    val campaignRepository: CampaignRepository,
    val scenarioReportRepository: ScenarioReportRepository,
    val scenarioReportMessageRepository: ScenarioReportMessageRepository,
    val reportFileBuilder: ReportFileBuilder,
    val templateReportService: TemplateReportService,
    val reportFileRepository: ReportFileRepository,
    val reportTaskRepository: ReportTaskRepository
) {
    private val campaignReferenceToName = mutableMapOf<String, String>()

    suspend fun processTaskGeneration(
        tenant: String,
        creator: String,
        reference: String,
        report: ReportEntity,
        reportTask: ReportTaskEntity,
    ) {
        logger.debug { "Report generation with tenant: $tenant, creator: $creator and reference: $reference, State: Processing" }
        val currentReportTempDir =
            Files.createTempDirectory("${report.displayName}-${reportTask.reference}").toAbsolutePath()
        try {
            val campaignData =
                campaignRepository.retrieveCampaignDetailByTenantIdAndKeyIn(
                    report.tenantId,
                    report.campaignKeys,
                    report.campaignNamesPatterns,
                    report.scenarioNamesPatterns
                ).map { it.toCampaignReportData() }
            //Fetch associated scenario reports.
            val scenarioReports = scenarioReportRepository.findByCampaignReportIdIn(
                campaignData.map { it.campaignReportId }
            )
            //Fetch associated scenario reports messages.
            val scenarioReportMessages = scenarioReportMessageRepository.findByScenarioReportIdInOrderById(
                scenarioReports.map { it.id }
            )
            val campaignReportData = campaignData.map { campaign ->
                campaignReferenceToName[campaign.campaignKey] = campaign.name
                val associatedScenarioReports =
                    scenarioReports.filter { it.campaignReportId == campaign.campaignReportId }
                val messagesByScenarioReportId = scenarioReportMessages.groupBy { it.scenarioReportId }
                val scenarioReport = associatedScenarioReports.map { scenarioReport ->
                    val messages =
                        (messagesByScenarioReportId[scenarioReport.id] ?: emptyList()).groupBy { it.severity }
                            .mapValues { it.value.size }

                    ScenarioReportData(
                        name = scenarioReport.name,
                        campaignReportId = scenarioReport.campaignReportId,
                        status = scenarioReport.status,
                        start = scenarioReport.start,
                        end = scenarioReport.end,
                        startedMinions = scenarioReport.startedMinions,
                        completedMinions = scenarioReport.completedMinions,
                        successfulExecutions = scenarioReport.successfulExecutions,
                        failedExecutions = scenarioReport.failedExecutions,
                        info = messages[ReportMessageSeverity.INFO] ?: 0,
                        warning = messages[ReportMessageSeverity.WARN] ?: 0,
                        error = (messages[ReportMessageSeverity.ERROR] ?: 0) + (messages[ReportMessageSeverity.ABORT] ?: 0)
                    )
                }
                campaign.scenarios = scenarioReport
                campaign.info = scenarioReport.sumOf { it.info }
                campaign.error = scenarioReport.sumOf { it.error }
                campaign.warning = scenarioReport.sumOf { it.warning }
                campaign.total = campaign.info + campaign.error + campaign.warning
                return@map campaign
            }
            require(campaignReportData.isNotEmpty()) { "No matching campaign for specified campaign keys, campaign name patterns and scenario name patterns" }
            val campaignReportDetail = reportFileBuilder.populateCampaignReportDetail(report, tenant, campaignReportData)
            val dataSeries = report.dataComponents.flatMap { it.dataSeries }
            val reportFile = templateReportService.generatePdf(
                report,
                campaignReportDetail,
                reportTask,
                creator,
                dataSeries,
                tenant,
                currentReportTempDir,
                campaignReferenceToName
            )
            reportFileRepository.save(
                ReportFileEntity(
                    "${report.displayName} ${Instant.now().truncatedTo(ChronoUnit.SECONDS)}",
                    reportFile,
                    Instant.now(),
                    reportTask.id
                )
            )
            reportTaskRepository.update(
                reportTask.copy(status = ReportTaskStatus.COMPLETED, updateTimestamp = Instant.now())
            )
            logger.info { "Report generation... Report Task state: Completed" }
        } catch (e: Exception) {
            reportTaskRepository.update(
                reportTask.copy(
                    status = ReportTaskStatus.FAILED,
                    updateTimestamp = Instant.now(),
                    failureReason = e.message
                )
            )
            logger.error(e) { "Encountered an error while generating report file : ${e.message}" }
            throw IllegalArgumentException("Encountered an error while generating report file : ${e.message}")
        } finally {
            File(currentReportTempDir.toString()).deleteRecursively()
        }
    }

    companion object {
        val logger = logger()
    }
}