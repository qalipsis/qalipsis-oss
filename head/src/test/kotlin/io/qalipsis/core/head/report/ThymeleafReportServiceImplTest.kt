/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.micronaut.core.io.ResourceLoader
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.model.ReportTaskStatus
import io.qalipsis.core.head.report.chart.ChartService
import io.qalipsis.core.head.report.thymeleaf.ThymeleafReportServiceImpl
import io.qalipsis.core.head.report.thymeleaf.catadioptre.buildContext
import io.qalipsis.core.head.report.thymeleaf.catadioptre.generatePdfFromHtml
import io.qalipsis.core.head.report.thymeleaf.catadioptre.loadFont
import io.qalipsis.core.head.report.thymeleaf.catadioptre.renderTemplate
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context


@WithMockk
@MicronautTest
internal class ThymeleafReportServiceImplTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var chartService: ChartService

    @MockK
    private lateinit var reportTaskRepository: ReportTaskRepository

    @MockK
    private lateinit var templateBeanFactory: TemplateBeanFactory

    @MockK
    private lateinit var resourceLoader: ResourceLoader

    private val tempDirectory = "displayName-reference"

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var templateReportService: ThymeleafReportServiceImpl

    private val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title></title>
                </head>
                <body>
                  <h1>Latest Meter Report</h1>
                </body>
                </html>
            """.trimIndent()

    @AfterEach
    fun cleanup() {
        File(tempDirectory).deleteRecursively()
    }

    @Test
    internal fun `should build the thymeleaf context`() {
        //given
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
        val campaignReportDetail = CampaignReportDetail(
            "Test title",
            listOf(campaignData, campaignData2),
            listOf(tableDataResult),
            relaxedMockk()
        )
        val chartImages = listOf("This is chart 1", "This is chart 2")

        //when
        val context = templateReportService.buildContext(campaignReportDetail, chartImages, "valid-font-url", Files.createTempDirectory(tempDirectory).toAbsolutePath())

        //then
        assertThat(context.getVariable("title")).isEqualTo("Test title")
        assertThat(context.getVariable("fontUrl")).isEqualTo("valid-font-url")
        assertThat(context.getVariable("chartImages")).isEqualTo(chartImages)
        assertThat(context.getVariable("campaigns")).isEqualTo(listOf(campaignData, campaignData2))
        assertThat(context.getVariable("eventTableData")).isEqualTo(null)
        assertThat(context.getVariable("meterTableData")).isEqualTo(listOf(tableDataResult))
    }

    @Test
    fun `Generates a pdf when given a HTML string`() {

        //when
        val result = templateReportService.generatePdfFromHtml(html)

        //then
        assertThat(result).isNotNull()
    }

    @Test
    fun `Throws an exception for empty HTML file`() {
        //when
        val caught = assertThrows<IllegalArgumentException> { templateReportService.generatePdfFromHtml("") }

        //then
        assertThat(caught).all {
            prop(IllegalArgumentException::message).isNotNull().isSameAs("HTML File is empty")
        }
    }

    @Test
    fun `Should generate pdf given a populated campaignReportDetail`() = testDispatcherProvider.runTest {
        //given
        val reportEntity = relaxedMockk<ReportEntity>()
        val campaignReportDetail = relaxedMockk<CampaignReportDetail>()
        val reportTaskEntity = ReportTaskEntity(
            id = 1,
            reportId = 11,
            reference = "report-ref",
            tenantReference = "tenant-ref",
            creationTimestamp = Instant.now(),
            updateTimestamp = Instant.now(),
            failureReason = null,
            creator = "user",
            status = ReportTaskStatus.PENDING,
        )
        val path = Files.createTempDirectory("displayNameReference").toAbsolutePath()
        coEvery { reportTaskRepository.update(any()) } returns reportTaskEntity.copy(status = ReportTaskStatus.PROCESSING)
        coEvery { templateBeanFactory.templateEngine() } returns TemplateEngine()
        coEvery { templateBeanFactory.templateEngine().process(any<String>(), any<Context>()) } returns html

        //when
        templateReportService.generatePdf(
            reportEntity,
            campaignReportDetail,
            reportTaskEntity,
            "user",
            listOf(),
            "tenant-1",
            path
        )

        //then
        coVerifyOnce {
            reportTaskRepository.update(withArg {
                assertThat(it).all {
                    prop(ReportTaskEntity::id).isEqualTo(1)
                    prop(ReportTaskEntity::reportId).isEqualTo(11)
                    prop(ReportTaskEntity::reference).isEqualTo("report-ref")
                    prop(ReportTaskEntity::tenantReference).isEqualTo("tenant-ref")
                    prop(ReportTaskEntity::status).isEqualTo(ReportTaskStatus.PROCESSING)
                    prop(ReportTaskEntity::creationTimestamp).isNotNull()
                    prop(ReportTaskEntity::updateTimestamp).isNotNull()
                    prop(ReportTaskEntity::failureReason).isNull()
                    prop(ReportTaskEntity::creator).isEqualTo("user")
                }
            })
            templateReportService.renderTemplate(campaignReportDetail, emptyList(), path)
            templateReportService.generatePdfFromHtml(any<String>())
        }
    }

    @Test
    fun `should render a template when given the right parameters`() {
        //given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            color = "color",
            colorOpacity = 12,
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            filters = setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
            )
        )
        val context = relaxedMockk<Context> { }
        val campaignReportDetail = relaxedMockk<CampaignReportDetail>()
        val path = Files.createTempDirectory(tempDirectory).toAbsolutePath()
        coEvery { templateBeanFactory.templateEngine() } returns TemplateEngine()
        coEvery { templateReportService.buildContext(any(), any(), any(), any()) } returns context

        //when
        templateReportService.renderTemplate(campaignReportDetail, listOf(dataSeriesEntity), path)

        //then
        coVerifyOnce {
            templateReportService.buildContext(campaignReportDetail, emptyList(), any(), any())
            templateReportService.loadFont(path)
        }
    }

    @Test
    fun `should load the font`() {
        //given
        val currentReportTempDir = Files.createTempDirectory(tempDirectory).toAbsolutePath()

        //when
        val result = templateReportService.loadFont(currentReportTempDir)

        //then
        val bufferedReader = File(result.toString()).bufferedReader()
        val fileContent = bufferedReader.use { it.readText() }
        assertNotNull(fileContent)
    }

}