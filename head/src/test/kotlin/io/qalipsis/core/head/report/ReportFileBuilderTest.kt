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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.test.annotation.MockBean
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.query.Page
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.ReportDataComponentEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignsInstantsAndDuration
import io.qalipsis.core.head.model.DataComponentType
import io.qalipsis.core.head.model.Zone
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.math.BigDecimal
import java.net.URL
import java.time.Duration
import java.time.Instant

@WithMockk
internal class ReportFileBuilderTest {
    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var timeSeriesDataQueryService: TimeSeriesDataQueryService

    @MockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @MockBean(HeadConfiguration::class)
    internal fun headConfiguration() = headConfiguration

    @InjectMockKs
    private lateinit var reportFileBuilder: ReportFileBuilder

    private val zones = setOf(
        Zone(
            "CAN",
            "canada",
            "This is US",
            image = URL("https://a-z-animals.com/media/2022/12/canada-flag.jpg_s1024x1024wisk20cc9uxuIyIwh1CwOOdAJtjpf-aPClkQuwIJ4gqa_7QLt0.jpg")
        ),
        Zone(
            "SA",
            "southafrica",
            "This is SA",
            image = URL("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTqn7alntSsK69xJRsSehEaeRjoh5XweLF9uQ&usqp=CAU")
        ),
        Zone(
            "GER",
            "Frankfurt",
            "This is frankfurt",
            image = URL("https://imgc.artprintimages.com/img/print/lantern-press-germany-country-flag-letterpress_u-l-q1jqoq50.jpg?background=f3f3f3")
        ),
        Zone("DM", "Denmark", "This is Denmark"),
        Zone("BAL", "Bali", "This is Bali")
    )

    private val reportEntity = ReportEntity(
        tenantId = 42L,
        displayName = "current-report",
        reference = "qoi78wizened",
        creatorId = 4L,
        campaignKeys = listOf("key1", "key2"),
        campaignNamesPatterns = emptyList(),
        scenarioNamesPatterns = emptyList(),
        dataComponents = listOf(
            ReportDataComponentEntity(
                id = 1, type = DataComponentType.DIAGRAM, -1, listOf(
                    DataSeriesEntity(
                        reference = "data-series-1",
                        tenantId = -1,
                        creatorId = -1,
                        displayName = "data-series-1",
                        dataType = DataType.METERS,
                        valueName = "my-value",
                        color = "#FF0000",
                        filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
                        timeframeUnitMs = 10_000L,
                        fieldName = "my-field",
                        aggregationOperation = QueryAggregationOperator.AVERAGE,
                        displayFormat = "#000.000",
                        query = "This is the query",
                        colorOpacity = null
                    )
                )
            ),
            ReportDataComponentEntity(
                id = 2, type = DataComponentType.DATA_TABLE, -1, listOf(
                    DataSeriesEntity(
                        reference = "data-series-2",
                        tenantId = -1,
                        creatorId = -1,
                        displayName = "data-series-2",
                        dataType = DataType.EVENTS,
                        valueName = "my-value2",
                        color = "#FF0000",
                        filters = setOf(DataSeriesFilterEntity("minionsCount", QueryClauseOperator.IS, "1000")),
                        timeframeUnitMs = 10_000L,
                        fieldName = "my-field",
                        aggregationOperation = QueryAggregationOperator.AVERAGE,
                        displayFormat = "#000.000",
                        query = "This is the query",
                        colorOpacity = null
                    )
                )
            )
        )
    )

    @Test
    fun `should return a populated campaign report detail`() = testDispatcherProvider.runTest {
        //given
        val aggregationResult = mapOf(
            "data-series-1" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.parse("PT2M27S"),
                    value = BigDecimal(40),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.parse("PT2M57S"),
                    value = BigDecimal(431),
                    campaign = "campaign-2"
                )
            ),
            "data-series-2" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.parse("PT1M27S"),
                    value = BigDecimal(3113),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.parse("PT59S"),
                    value = BigDecimal(2100),
                    campaign = "campaign-2"
                ),
            )
        )
        val tableData = mapOf(
            "data-series-1" to Page(
                page = 1, totalPages = 1, totalElements = 2, elements = listOf(
                    TimeSeriesMeter(
                        name = "Requests Made Meter",
                        timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                        campaign = "Campaign-1",
                        scenario = "cassandra.poll.polling.all",
                        value = BigDecimal(89208),
                        duration = Duration.ofMinutes(3).minusSeconds(33),
                        tags = null,
                        type = "Magic"
                    )
                )
            ),
            "data-series-2" to Page(
                page = 1, totalPages = 1, totalElements = 2, elements = listOf(
                    TimeSeriesMeter(
                        name = "Active minions",
                        timestamp = Instant.parse("2023-03-18T13:01:07.445312Z"),
                        campaign = "Campaign-2",
                        scenario = null,
                        value = null,
                        duration = null,
                        tags = null,
                        type = "Magic"
                    )
                )
            )
        )
        val campaignData = CampaignData(
            name = "campaign-1",
            result = ExecutionStatus.ABORTED,
            startedMinions = 6,
            completedMinions = 5,
            successfulExecutions = 5,
            failedExecutions = 1,
            zones = setOf("GER", "DM"),
            executionTime = 15
        )
        val campaignData2 = campaignData.copy(
            name = "campaign-2",
            result = ExecutionStatus.SUCCESSFUL,
            startedMinions = 46,
            completedMinions = 46,
            successfulExecutions = 25,
            failedExecutions = 21,
        )
        val tableDataResult = listOf(
            TimeSeriesMeter(
                name = "Requests Made Meter",
                timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                campaign = "Campaign-1",
                scenario = "cassandra.poll.polling.all",
                value = BigDecimal(89208),
                duration = Duration.ofMinutes(3).minusSeconds(33),
                tags = null,
                type = "Magic"
            ),
            TimeSeriesMeter(
                name = "Active minions",
                timestamp = Instant.parse("2023-03-18T13:01:07.445312Z"),
                campaign = "Campaign-2",
                scenario = null,
                value = null,
                duration = null,
                tags = null,
                type = "Magic"
            )
        )
        val campaignsInstantsAndDuration = CampaignsInstantsAndDuration(
            minStart = Instant.parse("2023-01-18T16:31:47.445312Z"),
            maxEnd = Instant.parse("2023-05-18T16:31:47.445312Z"),
            maxDurationSec = 42L
        )
        coEvery { timeSeriesDataQueryService.render(any(), any(), any()) } returns aggregationResult
        coEvery { timeSeriesDataQueryService.search(any(), any(), any()) } returns tableData
        coEvery { headConfiguration.cluster.zones } returns zones
        coEvery { campaignRepository.findInstantsAndDuration(any(), any()) } returns campaignsInstantsAndDuration

        //when
        val result = reportFileBuilder.populateCampaignReportDetail(
            reportEntity,
            "my-tenant",
            listOf(campaignData, campaignData2)
        )

        //then
        assertThat(result).all {
            prop(CampaignReportDetail::reportName).isEqualTo("current-report")
            prop(CampaignReportDetail::campaignData).isEqualTo(listOf(campaignData, campaignData2))
            prop(CampaignReportDetail::chartData).isEqualTo(
                listOf(
                    mapOf(
                        "data-series-1" to listOf(
                            TimeSeriesAggregationResult(
                                start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                                campaign = "campaign-1",
                                value = BigDecimal(40),
                                elapsed = Duration.parse("PT2M27S"),
                            ),
                            TimeSeriesAggregationResult(
                                elapsed = Duration.parse("PT2M57S"),
                                start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                                campaign = "campaign-2",
                                value = BigDecimal(431),
                            )
                        ),
                        "data-series-2" to listOf(
                            TimeSeriesAggregationResult(
                                start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                                campaign = "campaign-1",
                                value = BigDecimal(3113),
                                elapsed = Duration.parse("PT1M27S"),
                            ),
                            TimeSeriesAggregationResult(
                                start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                                campaign = "campaign-2",
                                value = BigDecimal(2100),
                                elapsed = Duration.parse("PT59S"),
                            )
                        )
                    ),
                )
            )
            prop(CampaignReportDetail::tableData).isEqualTo(listOf(tableDataResult))
        }
    }
}