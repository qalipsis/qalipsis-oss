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
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignsInstantsAndDuration
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.Zone
import jakarta.inject.Singleton

/**
 * Handles collation of properties for use in assembling a report file.
 *
 * @author Francisca Eze
 */
@Singleton
internal class ReportFileBuilder(
    private val timeSeriesDataQueryService: TimeSeriesDataQueryService,
    private val headConfiguration: HeadConfiguration,
    private val campaignRepository: CampaignRepository,
) {
    /**
     * Assemble other parts of the data needed to construct a report.
     *
     * @param report instance of the [ReportEntity] from which to populate report file.
     * @param tenant reference of the tenant owning the report
     * @param campaignData data class which contains all report data
     */
    suspend fun execute(
        report: ReportEntity,
        tenant: String,
        campaignData: Collection<CampaignData>
    ): CampaignReportDetail {
        campaignData.forEach { campaign ->
            val zones = mutableSetOf<Zone>()
            campaign.resolvedZones = headConfiguration.zones.filter { zone -> zone.key in campaign.zones }.toSet()
            campaign.apply { resolvedZones = zones }
        }
        val tableData = mutableListOf<Collection<TimeSeriesRecord>>()
        val chartData = mutableListOf<Map<String, List<TimeSeriesAggregationResult>>>()
        report.dataComponents.forEach { fetchDataByComponentType(tenant, report, it, tableData, chartData) }

        return CampaignReportDetail(
            reportName = report.displayName,
            campaignData = campaignData,
            tableData = tableData,
            chartData = chartData
        )
    }

    /**
     *  Fetches the needed time-series data for report charts or report tables.
     *
     *  @param tenant reference of the tenant owning the campaign
     *  @param report current [ReportEntity] to be displayed
     *  @param reportDataComponentEntity specifies the data component type
     *  @param tableData holds a collection of information needed to populate report tables
     *  @param chartData holds a collection of information needed to populate report chart
     */
    @KTestable
    private suspend fun fetchDataByComponentType(
        tenant: String,
        report: ReportEntity,
        reportDataComponentEntity: ReportDataComponentEntity,
        tableData: MutableCollection<Collection<TimeSeriesRecord>>,
        chartData: MutableCollection<Map<String, List<TimeSeriesAggregationResult>>>
    ) {
        val campaignReferences = report.campaignKeys.ifEmpty { report.campaignNamesPatterns }
        val dataSeries = reportDataComponentEntity.dataSeries.map { it.reference }.toSet()
        val campaignRange = campaignRepository.findInstantsAndDuration(tenant, report.campaignKeys)
        when (reportDataComponentEntity.type) {
            DataComponentType.DATA_TABLE -> tableData.add(
                fetchTableData(
                    tenant,
                    dataSeries,
                    campaignReferences,
                    report,
                    campaignRange!!
                )
            )

            DataComponentType.DIAGRAM -> chartData.add(
                fetchChartData(
                    tenant,
                    campaignReferences,
                    report,
                    dataSeries,
                    campaignRange!!
                )
            )
        }
    }

    /**
     * Fetches data from [TimeSeriesDataQueryService] to be used in populating report tables.
     */
    @KTestable
    private suspend fun fetchTableData(
        tenant: String,
        dataSeries: Set<String>,
        campaignReferences: Collection<String>,
        report: ReportEntity,
        campaignRange: CampaignsInstantsAndDuration
    ): List<TimeSeriesRecord> {
        val dataQueryExecutionRequest = DataRetrievalQueryExecutionRequest(
            tenant,
            campaignReferences.toSet(),
            report.scenarioNamesPatterns.toSet(),
            campaignRange.minStart!!,
            campaignRange.maxEnd!!
        )
        return timeSeriesDataQueryService
            .search(tenant, dataSeries, dataQueryExecutionRequest)
            .values.flatMap { it.elements }
    }

    /**
     * Fetches data from [TimeSeriesDataQueryService] to be used in populating chart tables.
     */
    @KTestable
    private suspend fun fetchChartData(
        tenant: String,
        campaignReferences: Collection<String>,
        report: ReportEntity,
        dataSeries: Set<String>,
        campaignRange: CampaignsInstantsAndDuration
    ): Map<String, List<TimeSeriesAggregationResult>> {
        val aggregationQueryRequest = AggregationQueryExecutionRequest(
            tenant = tenant,
            campaignsReferences = campaignReferences.toSet(),
            scenariosNames = report.scenarioNamesPatterns.toSet(),
            from = campaignRange.minStart,
            until = campaignRange.maxEnd,
        )
        return timeSeriesDataQueryService.render(tenant, dataSeries, aggregationQueryRequest)
    }
}