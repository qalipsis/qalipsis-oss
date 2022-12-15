package io.qalipsis.core.head.report

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.head.model.Zone

/**
 * Contains all the necessary details for generating a campaign report file.
 *
 * @property reportName title of the report
 * @property campaignData info to populate campaign summary  section of the report file
 * @property tableData nested collection of [TimeSeriesRecord] to build report tables
 * @property chartData nested map of data to build report charts
 *
 * @author Francisca Eze
 */
@Introspected
internal data class CampaignReportDetail(
    val reportName: String,
    val campaignData: Collection<CampaignData>,
    val tableData: Collection<Collection<TimeSeriesRecord>>,
    val chartData: Collection<Map<String, List<TimeSeriesAggregationResult>>>
)


/**
 * Contains info to populate campaign summary sections of the report file.
 *
 */
@Introspected
internal data class CampaignData(
    val name: String,
    val zones: Set<String> = emptySet(),
    val result: ExecutionStatus?,
    val executionTime: Long,
    val startedMinions: Int,
    val completedMinions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
) {
    lateinit var resolvedZones: Set<Zone>
}

