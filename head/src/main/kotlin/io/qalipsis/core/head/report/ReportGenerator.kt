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
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportFileEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
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
internal class ReportGenerator(
    val campaignRepository: CampaignRepository,
    val reportFileBuilder: ReportFileBuilder,
    val templateReportService: TemplateReportService,
    val reportFileRepository: ReportFileRepository,
    val reportTaskRepository: ReportTaskRepository
) {
    suspend fun processTaskGeneration(
        tenant: String,
        creator: String,
        reference: String,
        report: ReportEntity,
        reportTask: ReportTaskEntity
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
                )
            require(campaignData.isNotEmpty()) { "No matching campaign for specified campaign keys, campaign name patterns and scenario name patterns" }
            val campaignReportDetail = reportFileBuilder.execute(report, tenant, campaignData)
            val dataSeries = report.dataComponents.flatMap { it.dataSeries }
            val reportFile = templateReportService.generatePdf(
                report,
                campaignReportDetail,
                reportTask,
                creator,
                dataSeries,
                tenant,
                currentReportTempDir
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