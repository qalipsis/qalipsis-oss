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
package io.qalipsis.core.report.thymeleaf

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.spyk
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesEvent
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.report.CampaignData
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import io.qalipsis.core.head.report.chart.ChartServiceImpl
import io.qalipsis.core.head.report.chart.LineStyleGenerator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.math.BigDecimal
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64


@MicronautTest(startApplication = false)
internal class ReportTemplateFragmentTest {

    private lateinit var templateEngine: TemplateEngine

    private lateinit var snapshotDirectory: Path

    private lateinit var report: ReportEntity

    private lateinit var campaignDataPrototype: CampaignData

    private lateinit var context: Context

    private lateinit var lineStyleGenerator: LineStyleGenerator

    private lateinit var chartService: ChartServiceImpl

    private lateinit var dataSeriesEntity: DataSeriesEntity

    private val chartTestDir = Files.createTempDirectory("chart-test-images").toAbsolutePath()


    @BeforeEach
    fun init() {
        context = Context()
        createTemplateEngine()
        snapshotDirectory = Files.createTempDirectory("__snapshot__")
        report = ReportEntity(
            id = 5L,
            displayName = "Report Display Name",
            sharingMode = SharingMode.WRITE,
            reference = "report-reference",
            creatorId = 3L,
            tenantId = 2L,
            campaignKeys = listOf("campaign-1", "campaign-2", "campaign-3"),
            version = Instant.EPOCH,
            campaignNamesPatterns = listOf(),
            scenarioNamesPatterns = listOf(),
            dataComponents = listOf(),
            query = "report-query",
        )
        campaignDataPrototype = CampaignData(
            name = "Fifth Campaign",
            result = ExecutionStatus.ABORTED,
            startedMinions = 6,
            completedMinions = 5,
            successfulExecutions = 5,
            failedExecutions = 1,
            zones = emptySet(),
            executionTime = Instant.parse("2022-02-20T11:25:30.00Z").toEpochMilli().minus(Instant.now().toEpochMilli()),
        ).apply {
            resolvedZones = setOf(
                Zone(
                    "GER",
                    "Frankfurt",
                    "This is frankfurt",
                    imagePath = URL("https://imgc.artprintimages.com/img/print/lantern-press-germany-country-flag-letterpress_u-l-q1jqoq50.jpg?background=f3f3f3")
                ),
                Zone("DM", "Denmark", "This is Denmark"),
                Zone("BAL", "Bali", "This is Bali")
            )
        }
        dataSeriesEntity = DataSeriesEntity(
            reference = "data-series-1",
            tenantId = -1,
            creatorId = -1,
            displayName = "my-name",
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
        lineStyleGenerator = LineStyleGenerator()
        chartService = spyk(ChartServiceImpl(lineStyleGenerator))
    }

    @AfterAll
    fun cleanUp() {
        File(snapshotDirectory.toString()).deleteRecursively()
        File(chartTestDir.toString()).deleteRecursively()
    }

    private fun createTemplateEngine() {
        val resolver = ClassLoaderTemplateResolver().apply {
            templateMode = TemplateMode.HTML
            characterEncoding = CHARACTER_ENCODING
            prefix = RESOLVER_PREFIX
            suffix = RESOLVER_SUFFIX
        }
        templateEngine = TemplateEngine().apply {
            setTemplateResolver(resolver)
            addDialect(Java8TimeDialect())
        }
    }

    @Test
    fun `should render the button with its right execution status`(testInfo: TestInfo) {
        //given
        context.setVariable("status", ExecutionStatus.SCHEDULED)
        val buttonTemplate =
            """$HTML_OPENING_METADATA
                    <div class="button-wrapper SCHEDULED">
                      <span>SCHEDULED</span>
                    </div>
                  $HTML_CLOSING_METADATA"""
        //when
        val result = templateEngine.process("fragments/button", context)

        //then
        assertEqualHtml(result, buttonTemplate)
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the campaign list buttons with its right name`(testInfo: TestInfo) {
        //given
        context.setVariable(
            "campaigns", listOf(
                campaignDataPrototype, CampaignData(
                    name = "Campaign Seven",
                    result = ExecutionStatus.SUCCESSFUL,
                    startedMinions = 46,
                    completedMinions = 16,
                    successfulExecutions = 25,
                    failedExecutions = 21,
                    executionTime = 74,
                    zones = setOf("CAN", "SA"),
                ).apply {
                    resolvedZones = setOf(
                        Zone(
                            "CAN",
                            "canada",
                            "This is US",
                            imagePath = URL("https://a-z-animals.com/media/2022/12/canada-flag.jpg_s1024x1024wisk20cc9uxuIyIwh1CwOOdAJtjpf-aPClkQuwIJ4gqa_7QLt0.jpg")
                        ),
                        Zone(
                            "SA",
                            "southafrica",
                            "This is SA",
                            imagePath = URL("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTqn7alntSsK69xJRsSehEaeRjoh5XweLF9uQ&usqp=CAU")
                        ),
                    )
                }
            )
        )
        val campaignListTemplate =
            """<section class="campaign-list">
                 <div class="btn-transparent">
                   <span class="color"></span>
                   <h3 class="campaign-name">Fifth Campaign</h3>
                 </div>
                 <div class="btn-transparent">
                   <span class="color"></span>
                   <h3 class="campaign-name">Campaign Seven</h3>
                 </div>
             </section>
        """.trimIndent()

        //when
        val result = templateEngine.process("report-template", context)

        //then
        if (result.contains("""<div class="btn-transparent">""")) {
            val regex = Regex("""<section\s+class="campaign-list">(.|\n)*?</section>""")
            val match = regex.find(result)
            match?.let { assertEqualHtml(it.value, campaignListTemplate) } ?: assertEqualHtml("", campaignListTemplate)
        }
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the campaign summary`(testInfo: TestInfo) {
        //given
        context.setVariable(
            "campaign", CampaignData(
                name = "Campaign Seven",
                result = ExecutionStatus.SUCCESSFUL,
                startedMinions = 46,
                completedMinions = 46,
                successfulExecutions = 25,
                executionTime = 1691,
                failedExecutions = 21,
                zones = setOf("CN", "SA"),
            ).apply {
                resolvedZones = setOf(
                    Zone(
                        "CN",
                        "canada",
                        "This is US",
                        imagePath = URL("https://a-z-animals.com/media/2022/12/canada-flag.jpg_s1024x1024wisk20cc9uxuIyIwh1CwOOdAJtjpf-aPClkQuwIJ4gqa_7QLt0.jpg")
                    ),
                    Zone(
                        "SA",
                        "southafrica",
                        "This is South",
                        imagePath = URL("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTqn7alntSsK69xJRsSehEaeRjoh5XweLF9uQ&usqp=CAU")
                    ),
                )
            }
        )

        //when
        val result = templateEngine.process("fragments/campaign-summary", context)

        //then
        assertTrue(result.contains("""<div class="campaign-summary">"""))
        assertTrue(result.contains("""<h3 class="float-left pb-3">Campaign Seven</h3>"""))
        assertTrue(result.contains("""<span class="campaign-details-more-info-item-value">46</span>"""))
        assertTrue(result.contains("""<span class="campaign-details-more-info-item-value">25</span>"""))
        assertTrue(result.contains("""<span class="campaign-details-more-info-item-value">21</span>"""))
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the time series event table`(testInfo: TestInfo) {
        //given
        val timestamp1 =
            Instant.parse("2023-03-18T16:31:47.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val timestamp2 =
            Instant.parse("2023-03-18T09:21:47.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        context.setVariable(
            "singleEventTableData",
            setOf(
                TimeSeriesEvent(
                    name = "Requests Made",
                    timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    campaign = "Campaign 5 Longest",
                    scenario = "cassandra.save.saving.all",
                    number = BigDecimal(8368),
                    duration = Duration.ofHours(2).plusMinutes(15),
                    tags = null,
                    level = "Level 0"
                ),
                TimeSeriesEvent(
                    name = "Requests Per second",
                    timestamp = Instant.parse("2023-03-18T09:21:47.445312Z"),
                    campaign = null,
                    scenario = "elasticSearch.save.saving.all",
                    number = null,
                    duration = Duration.ofMinutes(3).minusSeconds(33),
                    tags = mapOf("zone1" to "EU", "zone2" to "BR", "zone3" to "US"),
                    level = "Level 0"
                )
            ),
        )
        val eventTableTemplate = """
            ${HTML_OPENING_METADATA.stripNewlines()}
            <table class="data-series-section">
              ${EVENT_TABLE_HEADERS.stripNewlines()}
              <tr>
                <td class="data-series-table-data" timestamp="2023-03-18T16:31:47.445312Z">${timestamp1}</td>
                <td class="data-series-table-data">Requests Made</td>
                <td class="data-series-table-data">Campaign 5 Longest</td>
                <td class="data-series-table-data">cassandra.save.saving.all</td>
                <td class="data-series-table-data">8368</td>
                <td class="data-series-table-data">8100secs</td>
                <td class="data-series-table-data">--</td>
              </tr>
              <tr>
                <td class="data-series-table-data" timestamp="2023-03-18T09:21:47.445312Z">${timestamp2}</td>
                <td class="data-series-table-data">Requests Per second</td>
                <td class="data-series-table-data">--</td>
                <td class="data-series-table-data">elasticSearch.save.saving.all</td>
                <td class="data-series-table-data">--</td>
                <td class="data-series-table-data">147secs</td>
                <td class="data-series-table-data">
                  <div class="td-tag">
                    <span class="tag">zone1=EU, </span><span class="tag">zone2=BR, </span><span class="tag">zone3=US, </span>
                  </div>
                </td>
              </tr>
            </table>
            ${HTML_CLOSING_METADATA.stripNewlines()}
            """.trimIndent()

        //when
        val result = templateEngine.process("fragments/time-series-event-table", context)

        //then
        assertEqualHtml(eventTableTemplate, result)
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render no table when the time series event is empty`(testInfo: TestInfo) {
        //given
        context.setVariable("singleEventTableData", emptySet<TimeSeriesRecord>())
        val emptyEventTableTemplate =
            """${HTML_OPENING_METADATA.stripNewlines()}
                <table class="data-series-section">
                  ${EVENT_TABLE_HEADERS.stripNewlines()}
                  <tr>
                    <td class="data-series-table-data empty-table" colspan="7" style="text-align:center"> No data Available</td>
                  </tr>
                </table>
            ${HTML_CLOSING_METADATA.stripNewlines()}
        """.trimIndent()

        //when
        val result = templateEngine.process("fragments/time-series-event-table", context)

        //then
        assertEqualHtml(emptyEventTableTemplate, result)
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render no table when the time series meter is empty`(testInfo: TestInfo) {
        //given
        context.setVariable("singleMeterTableData", emptySet<TimeSeriesRecord>())
        val emptyMeterTableTemplate = """
           ${HTML_OPENING_METADATA.stripNewlines()}
            <table class="data-series-section">
              ${METER_TABLE_HEADERS.stripNewlines()}
              <tr>
                <td class="data-series-table-data empty-table" colspan="6"> No data Available</td>
              </tr>
            </table>
           ${HTML_CLOSING_METADATA.stripNewlines()}
        """.trimIndent()

        //when
        val result = templateEngine.process("fragments/time-series-meter-table", context)

        //then
        assertEqualHtml(emptyMeterTableTemplate, result)
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the time series meter table`(testInfo: TestInfo) {
        val timestamp1 =
            Instant.parse("2023-03-18T16:31:47.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val timestamp2 =
            Instant.parse("2023-03-18T13:01:07.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        //given
        context.setVariable(
            "singleMeterTableData", setOf(
                TimeSeriesMeter(
                    name = "Requests Made Meter",
                    timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    campaign = "Campaign 8",
                    scenario = "cassandra.poll.polling.all",
                    value = BigDecimal(89208),
                    duration = Duration.ofMinutes(3).minusSeconds(33),
                    tags = null,
                    type = "Magic"
                ),
                TimeSeriesMeter(
                    name = "Active minions",
                    timestamp = Instant.parse("2023-03-18T13:01:07.445312Z"),
                    campaign = "Campaign Nine",
                    scenario = null,
                    value = null,
                    duration = null,
                    tags = null,
                    type = "Magic"
                )
            )
        )
        val meterTableTemplate = """
            ${HTML_OPENING_METADATA.stripNewlines()}
            <table class="data-series-section">
              ${METER_TABLE_HEADERS.stripNewlines()}
              <tr>
                <td class="data-series-table-data" timestamp="2023-03-18T16:31:47.445312Z">${timestamp1}</td>
                <td class="data-series-table-data">Requests Made Meter</td>
                <td class="data-series-table-data">Campaign 8</td>
                <td class="data-series-table-data">cassandra.poll.polling.all</td>
                <td class="data-series-table-data">147secs</td>
                <td class="data-series-table-data">--</td>
              </tr>
              <tr>
                <td class="data-series-table-data" timestamp="2023-03-18T13:01:07.445312Z">${timestamp2}</td>
                <td class="data-series-table-data">Active minions</td>
                <td class="data-series-table-data">Campaign Nine</td>
                <td class="data-series-table-data">--</td>
                <td class="data-series-table-data">--</td>
                <td class="data-series-table-data">--</td>
              </tr>
            </table>
            ${HTML_CLOSING_METADATA.stripNewlines()}
        """.trimIndent()

        //when
        val result = templateEngine.process("fragments/time-series-meter-table", context)

        //then
        assertEqualHtml(meterTableTemplate, result)
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the chart data`(testInfo: TestInfo) {
        //given
        val aggregationResult = mapOf(
            "data-series-1" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5365547),
                    value = BigDecimal(40),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.423412Z"),
                    elapsed = Duration.ofNanos(6714011),
                    value = BigDecimal(93),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7240177),
                    value = BigDecimal(231),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8144072),
                    value = BigDecimal(621),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(66412659),
                    value = BigDecimal(708),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71146859),
                    value = BigDecimal(921),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71554376),
                    value = BigDecimal(11),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(85144072),
                    value = BigDecimal(431),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(65385417),
                    value = BigDecimal(93),
                    campaign = "campaign-4"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7485427),
                    value = BigDecimal(78),
                    campaign = "campaign-4"
                ),
            ),
            "data-series-2" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62133262),
                    value = BigDecimal(3113),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(51196872),
                    value = BigDecimal(2100),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-3" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(1206519),
                    value = BigDecimal(112222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3245874),
                    value = BigDecimal(100000),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6845167),
                    value = BigDecimal(95542),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6128674),
                    value = BigDecimal(10222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(49068266),
                    value = BigDecimal(164),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62352851),
                    value = BigDecimal(323),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-4" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3622647),
                    value = BigDecimal(8932),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8621647),
                    value = BigDecimal(14222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5622647),
                    value = BigDecimal(782422),
                    campaign = "campaign-5"
                ),
            )
        )

        val dataSeries = listOf(
            dataSeriesEntity,
            dataSeriesEntity.copy(reference = "data-series-2", color = null, colorOpacity = 23),
            dataSeriesEntity.copy(reference = "data-series-3", color = null, colorOpacity = null),
            dataSeriesEntity.copy(reference = "data-series-4", color = "#000000", colorOpacity = 50),
        )
        val base64ImagePath = chartService.buildChart(aggregationResult, dataSeries, 0, chartTestDir)
        val imageByte = Files.readAllBytes(base64ImagePath).toBase64()
        val chartImageTemplate = """
            <img class="img" src="$imageByte" alt="pdf-image Requests Chart"/>
        """.trimIndent().stripNewlines()
        context.setVariable("chartImages", listOf(Files.readAllBytes(base64ImagePath).toBase64()))

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assert(result.contains(chartImageTemplate))
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the empty chart section when chart data is empty`(testInfo: TestInfo) {
        //given
        val noChartMessage = """
            <p>No Chart available</p>
        """.trimIndent().stripNewlines()
        val emptyList = emptyList<String>()
        context.setVariable("chartImages", emptyList)

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assert(result.contains(noChartMessage))
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the empty event table section when the list of time series event is empty`(testInfo: TestInfo) {
        //given
        context.setVariable("eventTableData", emptySet<TimeSeriesRecord>())
        val emptyEventListText = """
             <p>No TimeSeries Record available</p>
        """.trimIndent()

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assert(result.contains(emptyEventListText))
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the empty meter table section when the list of time series meter is empty`(testInfo: TestInfo) {
        //given
        context.setVariable("meterTableData", emptySet<TimeSeriesRecord>())
        val emptyEventListText = """
             <p>No TimeSeries Record available</p>
        """.trimIndent()

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assert(result.contains(emptyEventListText))
        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the entire report with multiple event table data and multiple chartData`(testInfo: TestInfo) {
        //given
        val aggregationResult = mapOf(
            "data-series-1" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5365547),
                    value = BigDecimal(40),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.423412Z"),
                    elapsed = Duration.ofNanos(6714011),
                    value = BigDecimal(93),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7240177),
                    value = BigDecimal(231),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8144072),
                    value = BigDecimal(621),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(66412659),
                    value = BigDecimal(708),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71146859),
                    value = BigDecimal(921),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71554376),
                    value = BigDecimal(11),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(85144072),
                    value = BigDecimal(431),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(65385417),
                    value = BigDecimal(93),
                    campaign = "campaign-4"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7485427),
                    value = BigDecimal(78),
                    campaign = "campaign-4"
                ),
            ),
            "data-series-2" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62133262),
                    value = BigDecimal(3113),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(51196872),
                    value = BigDecimal(2100),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-3" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(1206519),
                    value = BigDecimal(112222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3245874),
                    value = BigDecimal(100000),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6845167),
                    value = BigDecimal(95542),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6128674),
                    value = BigDecimal(10222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(49068266),
                    value = BigDecimal(164),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62352851),
                    value = BigDecimal(323),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-4" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3622647),
                    value = BigDecimal(8932),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8621647),
                    value = BigDecimal(14222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5622647),
                    value = BigDecimal(782422),
                    campaign = "campaign-5"
                ),
            )
        )
        val aggregationResult2 = mapOf(
            "data-series-5" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5322547),
                    value = BigDecimal(240),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.423412Z"),
                    elapsed = Duration.ofNanos(6712111),
                    value = BigDecimal(193),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7990177),
                    value = BigDecimal(239),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(2344072),
                    value = BigDecimal(622),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(66412899),
                    value = BigDecimal(702),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71146339),
                    value = BigDecimal(921),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71554376),
                    value = BigDecimal(15),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(85145662),
                    value = BigDecimal(453),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(65115417),
                    value = BigDecimal(76),
                    campaign = "campaign-4"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7400427),
                    value = BigDecimal(178),
                    campaign = "campaign-4"
                ),
            ),
            "data-series-6" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62133292),
                    value = BigDecimal(5413),
                    campaign = "campaign-7"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(51196870),
                    value = BigDecimal(7100),
                    campaign = "campaign-7"
                ),
            ),
            "data-series-7" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(1244419),
                    value = BigDecimal(444222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5675874),
                    value = BigDecimal(109990),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6842367),
                    value = BigDecimal(955784),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(9990674),
                    value = BigDecimal(13422),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(49164366),
                    value = BigDecimal(1643),
                    campaign = "campaign-8"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62332351),
                    value = BigDecimal(3230),
                    campaign = "campaign-8"
                ),
            ),
            "data-series-8" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3932647),
                    value = BigDecimal(98132),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8442147),
                    value = BigDecimal(18422),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7622607),
                    value = BigDecimal(789922),
                    campaign = "campaign-5"
                ),
            )
        )
        val dataSeries = listOf(
            dataSeriesEntity,
            dataSeriesEntity.copy(reference = "data-series-2", color = "#800080", colorOpacity = 23),
            dataSeriesEntity.copy(reference = "data-series-3", color = null, colorOpacity = null),
            dataSeriesEntity.copy(reference = "data-series-4", color = "#6a329f", colorOpacity = 50),
            dataSeriesEntity.copy(reference = "data-series-5", color = "#008000", colorOpacity = 60),
            dataSeriesEntity.copy(reference = "data-series-6", color = "#f08080", colorOpacity = 100),
            dataSeriesEntity.copy(reference = "data-series-7", color = null, colorOpacity = 83),
            dataSeriesEntity.copy(reference = "data-series-8", color = "#3a3500", colorOpacity = 10),
        )
        val campaignList = listOf(
            campaignDataPrototype, campaignDataPrototype.copy(
                name = "Campaign Seven",
                result = ExecutionStatus.SUCCESSFUL,
                startedMinions = 46,
                completedMinions = 16,
                successfulExecutions = 25,
                failedExecutions = 21,
            ).apply {
                resolvedZones = setOf(
                    Zone(
                        "CAN",
                        "canada",
                        "This is US",
                        imagePath = URL("https://a-z-animals.com/media/2022/12/canada-flag.jpg_s1024x1024wisk20cc9uxuIyIwh1CwOOdAJtjpf-aPClkQuwIJ4gqa_7QLt0.jpg")
                    ),
                    Zone(
                        "SA",
                        "southafrica",
                        "This is SA",
                        imagePath = URL("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTqn7alntSsK69xJRsSehEaeRjoh5XweLF9uQ&usqp=CAU")
                    ),
                )
            }
        )
        val timestamp1 =
            Instant.parse("2023-03-16T09:15:22.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val timestamp2 =
            Instant.parse("2023-03-13T15:09:41.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val eventTableData1 = setOf(
            TimeSeriesEvent(
                name = "Requests Made",
                timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                campaign = "Campaign 5 Longest",
                scenario = "cassandra.save.saving.all",
                number = BigDecimal(8368),
                duration = Duration.ofHours(2).plusMinutes(15),
                tags = null,
                level = "Level 0"
            ),
            TimeSeriesEvent(
                name = "Requests Per second",
                timestamp = Instant.parse("2023-03-18T09:21:47.445312Z"),
                campaign = null,
                scenario = "elasticSearch.save.saving.all",
                number = null,
                duration = Duration.ofMinutes(3).minusSeconds(33),
                tags = mapOf("zone1" to "EU", "zone2" to "BR", "zone3" to "US"),
                level = "Level 0"
            )
        )
        val eventTableData2 = setOf(
            TimeSeriesEvent(
                name = "Requests Made2",
                timestamp = Instant.parse("2023-03-13T15:09:41.445312Z"),
                campaign = "Campaign 5 Longest Again",
                scenario = "cassandra.save.saving.all2",
                number = BigDecimal(8368),
                duration = Duration.ofHours(2).plusMinutes(25),
                tags = null,
                level = "Level 0"
            ),
            TimeSeriesEvent(
                name = "Requests Per second2",
                timestamp = Instant.parse("2023-03-16T09:15:22.445312Z"),
                campaign = null,
                scenario = "elasticSearch.save.saving.all2",
                number = BigDecimal(8168),
                duration = Duration.ofMinutes(8).minusSeconds(13),
                tags = mapOf("zone1" to "EU", "zone2" to "BR", "zone3" to "US"),
                level = "Level 0"
            )
        )

        val title = "Latest Event Report"
        val base64ImagePath1 = chartService.buildChart(aggregationResult, dataSeries, 0, chartTestDir)
        val base64ImagePath2 = chartService.buildChart(aggregationResult2, dataSeries, 1, chartTestDir)
        context.apply {
            setVariable("title", title)
            setVariable("campaigns", campaignList)
            setVariable("eventTableData", listOf(eventTableData1, eventTableData2))
            setVariable(
                "chartImages",
                listOf(Files.readAllBytes(base64ImagePath1).toBase64(), Files.readAllBytes(base64ImagePath2).toBase64())
            )
        }

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assertTrue { result.contains("""<h1 class="float-left">Latest Event Report</h1>""") }
        assertTrue { result.contains("""<div class="campaign-summary">""") }
        assertTrue { result.contains("""<h3 class="campaign-name">Campaign Seven</h3>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">46</span>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">25</span>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">21</span>""") }
        assertTrue { result.contains("""<td class="data-series-table-data" timestamp="2023-03-16T09:15:22.445312Z">${timestamp1}</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Requests Per second2</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">cassandra.save.saving.all2</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Campaign 5 Longest Again</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data" timestamp="2023-03-13T15:09:41.445312Z">${timestamp2}</td>""".trimMargin()) }
        assertTrue { result.contains("""<td class="data-series-table-data">elasticSearch.save.saving.all2</td>""") }

        writeSnapshot(testInfo.displayName, result)
    }

    @Test
    fun `should render the entire report with multiple meter table data and multiple chartData`(testInfo: TestInfo) {
        //given
        val aggregationResult = mapOf(
            "data-series-1" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5365547),
                    value = BigDecimal(40),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.423412Z"),
                    elapsed = Duration.ofNanos(6714011),
                    value = BigDecimal(93),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7240177),
                    value = BigDecimal(231),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8144072),
                    value = BigDecimal(621),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(66412659),
                    value = BigDecimal(708),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71146859),
                    value = BigDecimal(921),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71554376),
                    value = BigDecimal(11),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(85144072),
                    value = BigDecimal(431),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(65385417),
                    value = BigDecimal(93),
                    campaign = "campaign-4"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7485427),
                    value = BigDecimal(78),
                    campaign = "campaign-4"
                ),
            ),
            "data-series-2" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62133262),
                    value = BigDecimal(3113),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(51196872),
                    value = BigDecimal(2100),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-3" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(1206519),
                    value = BigDecimal(112222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3245874),
                    value = BigDecimal(100000),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6845167),
                    value = BigDecimal(95542),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6128674),
                    value = BigDecimal(10222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(49068266),
                    value = BigDecimal(164),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62352851),
                    value = BigDecimal(323),
                    campaign = "campaign-1"
                ),
            ),
            "data-series-4" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3622647),
                    value = BigDecimal(8932),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8621647),
                    value = BigDecimal(14222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5622647),
                    value = BigDecimal(782422),
                    campaign = "campaign-5"
                ),
            )
        )
        val aggregationResult2 = mapOf(
            "data-series-5" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5322547),
                    value = BigDecimal(240),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.423412Z"),
                    elapsed = Duration.ofNanos(6712111),
                    value = BigDecimal(193),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7990177),
                    value = BigDecimal(239),
                    campaign = "campaign-1"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(2344072),
                    value = BigDecimal(622),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(66412899),
                    value = BigDecimal(702),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71146339),
                    value = BigDecimal(921),
                    campaign = "campaign-2"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(71554376),
                    value = BigDecimal(15),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(85145662),
                    value = BigDecimal(453),
                    campaign = "campaign-3"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(65115417),
                    value = BigDecimal(76),
                    campaign = "campaign-4"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7400427),
                    value = BigDecimal(178),
                    campaign = "campaign-4"
                ),
            ),
            "data-series-6" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62133292),
                    value = BigDecimal(5413),
                    campaign = "campaign-7"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(51196870),
                    value = BigDecimal(7100),
                    campaign = "campaign-7"
                ),
            ),
            "data-series-7" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(1244419),
                    value = BigDecimal(444222),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(5675874),
                    value = BigDecimal(109990),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(6842367),
                    value = BigDecimal(955784),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(9990674),
                    value = BigDecimal(13422),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(49164366),
                    value = BigDecimal(1643),
                    campaign = "campaign-8"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(62332351),
                    value = BigDecimal(3230),
                    campaign = "campaign-8"
                ),
            ),
            "data-series-8" to listOf
                (
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(3932647),
                    value = BigDecimal(98132),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(8442147),
                    value = BigDecimal(18422),
                    campaign = "campaign-5"
                ),
                TimeSeriesAggregationResult(
                    start = Instant.parse("2023-03-18T16:31:47.445312Z"),
                    elapsed = Duration.ofNanos(7622607),
                    value = BigDecimal(789922),
                    campaign = "campaign-5"
                ),
            )
        )
        val dataSeries = listOf(
            dataSeriesEntity,
            dataSeriesEntity.copy(reference = "data-series-2", color = "#800080", colorOpacity = 23),
            dataSeriesEntity.copy(reference = "data-series-3", color = null, colorOpacity = null),
            dataSeriesEntity.copy(reference = "data-series-4", color = "#6a329f", colorOpacity = 50),
            dataSeriesEntity.copy(reference = "data-series-5", color = "#008000", colorOpacity = 60),
            dataSeriesEntity.copy(reference = "data-series-6", color = "#f08080", colorOpacity = 100),
            dataSeriesEntity.copy(reference = "data-series-7", color = null, colorOpacity = 83),
            dataSeriesEntity.copy(reference = "data-series-8", color = "#3a3500", colorOpacity = 10),
        )
        val campaignList = listOf(
            campaignDataPrototype, campaignDataPrototype.copy(
                name = "Campaign Seven",
                result = ExecutionStatus.SUCCESSFUL,
                startedMinions = 46,
                completedMinions = 16,
                successfulExecutions = 25,
                failedExecutions = 21,
            ).apply {
                resolvedZones = setOf(
                    Zone(
                        "CAN",
                        "canada",
                        "This is US",
                        imagePath = URL("https://a-z-animals.com/media/2022/12/canada-flag.jpg_s1024x1024wisk20cc9uxuIyIwh1CwOOdAJtjpf-aPClkQuwIJ4gqa_7QLt0.jpg")
                    ),
                    Zone(
                        "SA",
                        "southafrica",
                        "This is SA",
                        imagePath = URL("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTqn7alntSsK69xJRsSehEaeRjoh5XweLF9uQ&usqp=CAU")
                    ),
                )
            }
        )
        val timestamp1 =
            Instant.parse("2023-03-18T13:01:07.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val timestamp2 =
            Instant.parse("2023-03-18T16:31:47.445312Z").atZone(ZoneId.systemDefault()).toLocalTime().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
            )
        val meterTableData = setOf(
            TimeSeriesMeter(
                name = "Requests Made Meter",
                timestamp = Instant.parse("2023-03-18T16:31:47.445312Z"),
                campaign = "Campaign 8",
                scenario = "cassandra.poll.polling.all",
                value = BigDecimal(89208),
                duration = Duration.ofMinutes(3).minusSeconds(33),
                tags = null,
                type = "Magic"
            ),
            TimeSeriesMeter(
                name = "Active minions",
                timestamp = Instant.parse("2023-03-18T13:01:07.445312Z"),
                campaign = "Campaign Nine",
                scenario = null,
                value = null,
                duration = null,
                tags = null,
                type = "Magic"
            )
        )

        val title = "Latest Meter Report"
        val base64Image1 = chartService.buildChart(aggregationResult, dataSeries, 0, chartTestDir)
        val base64Image2 = chartService.buildChart(aggregationResult2, dataSeries, 1, chartTestDir)
        context.apply {
            setVariable("title", title)
            setVariable("campaigns", campaignList)
            setVariable("meterTableData", listOf(meterTableData))
            setVariable(
                "chartImages",
                listOf(Files.readAllBytes(base64Image1).toBase64(), Files.readAllBytes(base64Image2).toBase64())
            )
        }

        //when
        val result = templateEngine.process("report-template", context)

        //then
        assertTrue { result.contains("""<h1 class="float-left">Latest Meter Report</h1>""") }
        assertTrue { result.contains("""<div class="campaign-summary">""") }
        assertTrue { result.contains("""<h3 class="campaign-name">Campaign Seven</h3>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">46</span>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">25</span>""") }
        assertTrue { result.contains("""<span class="campaign-details-more-info-item-value">21</span>""") }
        assertTrue { result.contains("""<td class="data-series-table-data" timestamp="2023-03-18T13:01:07.445312Z">${timestamp1}</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Active minions</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">cassandra.poll.polling.all</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Campaign 8</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Requests Made Meter</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">Campaign Nine</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data">147secs</td>""") }
        assertTrue { result.contains("""<td class="data-series-table-data" timestamp="2023-03-18T16:31:47.445312Z">${timestamp2}</td>""") }

        writeSnapshot(testInfo.displayName, result)
    }

    private fun writeSnapshot(name: String, result: String) {
        try {
            FileWriter("$snapshotDirectory/$name.html").use { fileWriter -> fileWriter.write(result) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Compare two string fragments.
     */
    private fun assertEqualHtml(expected: String, actual: String) {
        val pattern = Regex(">\\s+<") // pattern to match whitespace between tags.
        val strippedHtml1 = expected.replace(pattern, "><")
        val strippedHtml2 = actual.replace(pattern, "><")
        assertEquals(strippedHtml1, strippedHtml2)
    }

    /**
     * Strip newline characters from strings.
     */
    private fun String.stripNewlines() = this.trim().replace(Regex("/[\n\r]/g"), "")

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    private companion object {

        private const val HTML_OPENING_METADATA = """<!DOCTYPE html><html lang="en"><body>"""

        private const val HTML_CLOSING_METADATA = "</body></html>"

        private const val EVENT_TABLE_HEADERS = """
            <tr class="header-table-row">
                <th class="data-series-table-head">Timestamp</th>
                <th class="data-series-table-head">Name of the Series</th>
                <th class="data-series-table-head">Campaign</th>
                <th class="data-series-table-head">Scenario Name</th>
                <th class="data-series-table-head">Number</th>
                <th class="data-series-table-head">Duration</th>
                <th class="data-series-table-head">Tags</th>
            </tr>
        """
        private const val METER_TABLE_HEADERS = """
            <tr class="header-table-row">
                <th class="data-series-table-head">Timestamp</th>
                <th class="data-series-table-head">Name of the Series</th>
                <th class="data-series-table-head">Campaign</th>
                <th class="data-series-table-head">Scenario Name</th>
                <th class="data-series-table-head">Duration</th>
                <th class="data-series-table-head">Tags</th>
            </tr>
        """

        const val CHARACTER_ENCODING = "UTF-8"

        const val RESOLVER_PREFIX = "/views/"

        const val RESOLVER_SUFFIX = ".html"
    }
}