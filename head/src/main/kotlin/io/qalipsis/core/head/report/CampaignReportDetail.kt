/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.api.report.TimeSeriesValues
import io.qalipsis.core.head.model.Zone
import java.time.Duration
import java.time.Instant

/**
 * Contains all the necessary details for generating a campaign report file.
 *
 * @property reportName title of the report
 * @property campaignReportData info to populate campaign summary  section of the report file
 * @property tableData nested collection of [TimeSeriesRecord] to build report tables
 * @property chartData nested map of data to build report charts
 *
 * @author Francisca Eze
 */
@Introspected
data class CampaignReportDetail(
    val reportName: String,
    val campaignReportData: Collection<CampaignReportData>,
    val tableData: Collection<Collection<TimeSeriesRecord>>,
    val chartData: Collection<Map<String, TimeSeriesValues>>
)


/**
 * Contains campaign report detail retrieved from the database.
 */
@Introspected
data class CampaignData(
    val campaignKey: String,
    val name: String,
    val campaignReportId: Long,
    val zones: Set<String> = emptySet(),
    val result: ExecutionStatus?,
    val start: Instant?,
    val executionTime: Long,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int
) {
    /**
     * Converts [CampaignData] to [CampaignReportData].
     */
    fun toCampaignReportData() = CampaignReportData(
        campaignKey = campaignKey,
        name = name,
        campaignReportId = campaignReportId,
        zones = zones,
        result = result,
        start = start,
        executionTime = executionTime,
        startedMinions = startedMinions,
        completedMinions = completedMinions,
        successfulExecutions = successfulExecutions,
        failedExecutions = failedExecutions
    )
}

/**
 * Contains info to populate campaign summary sections of the report file along with its scenarios details.
 */
@Introspected
data class CampaignReportData(
    val campaignKey: String,
    val name: String,
    val campaignReportId: Long,
    val zones: Set<String> = emptySet(),
    val result: ExecutionStatus?,
    val start: Instant?,
    val executionTime: Long,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    var resolvedZones: Set<Zone> = emptySet(),
    var total: Int = 0,
    var info: Int = 0,
    var warning: Int = 0,
    var error: Int = 0,
    var scenarios: Collection<ScenarioReportData> = emptyList()
)

/**
 * Contains scenario report detail retrieved from the database.
 */
data class ScenarioReportData(
    val name: String,
    val campaignReportId: Long,
    val status: ExecutionStatus?,
    val start: Instant,
    val end: Instant,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val info: Int,
    val warning: Int,
    val error: Int,
    val duration: String = Duration.between(start, end).toReadableString()
)

/**
 * Converts a [Duration] to a readable string format.
 * Examples: "2d 3h 15m", "4h 30m", "45m", "30s"
 */
fun Duration.toReadableString(): String {
    if (this == Duration.ZERO) return "0s"

    val days = seconds / (24 * 3600)
    val hours = (seconds % (24 * 3600)) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        if (secs > 0 || (days == 0L && hours == 0L && minutes == 0L)) {
            append("${secs}s")
        }
    }.trim()
}