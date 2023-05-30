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

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import jakarta.inject.Singleton
import java.time.Instant

/**
 * Scheduler class that deletes report tasks and files older than the configured time.
 *
 * @author Francisca Eze
 */
@Singleton
@Requirements(
    Requires(beans = [ReportRecordsTTLConfiguration::class, ReportTaskRepository::class, ReportFileRepository::class]),
    Requires(notEnv = [AUTOSTART])
)
internal class ReportsPurgeManager(
    private val reportRecordsConfiguration: ReportRecordsTTLConfiguration,
    private val reportTaskRepository: ReportTaskRepository,
    private val reportFileRepository: ReportFileRepository,
) {

    /**
     * Runs at the configured time to prune stale report records, defaults to 24 hours.
     */
    @Scheduled(cron = "\${report.records.cron:0 0 0 1/1 * ?}")
    suspend fun executeTask() {
        try {
            this.pruneReportTaskRecords()
            this.pruneReportFileRecords()
        } catch (ex: Exception) {
            logger.error(ex) { ex.message }
            throw ex
        }
    }

    /**
     * Deletes report tasks older than the specified [ReportRecordsTTLConfiguration.taskTimeToLive]
     * from the database.
     */
    @KTestable
    private suspend fun pruneReportTaskRecords() {
        logger.info("Starting report tasks deletion")
        val minimalTaskRecordsTimeToLive = Instant.now().minus(reportRecordsConfiguration.taskTimeToLive)
        reportTaskRepository.deleteAllByUpdateTimestampLessThan(minimalTaskRecordsTimeToLive)
        logger.info("Report tasks deletion completed")
    }

    /**
     * Deletes report files older than the specified [ReportRecordsTTLConfiguration.fileTimeToLive]
     * from the database.
     */
    @KTestable
    private suspend fun pruneReportFileRecords() {
        logger.info("Starting report files deletion")
        val minimalFileRecordsTimeToLive = Instant.now().minus(reportRecordsConfiguration.fileTimeToLive)
        reportFileRepository.deleteAllByCreationTimestampLessThan(minimalFileRecordsTimeToLive)
        logger.info("Report files deletion completed")
    }

    private companion object {
        val logger = logger()
    }

}