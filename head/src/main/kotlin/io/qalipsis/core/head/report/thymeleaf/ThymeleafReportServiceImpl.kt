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
package io.qalipsis.core.head.report.thymeleaf

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.report.TimeSeriesEvent
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.ReportEntity
import io.qalipsis.core.head.jdbc.entity.ReportTaskEntity
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.core.head.model.ReportTaskStatus
import io.qalipsis.core.head.report.CampaignReportDetail
import io.qalipsis.core.head.report.TemplateBeanFactory
import io.qalipsis.core.head.report.TemplateReportService
import io.qalipsis.core.head.report.chart.ChartService
import jakarta.inject.Singleton
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.io.path.absolutePathString


/**
 * Custom implementation of [TemplateReportService] to build report template.
 *
 * @author Francisca Eze
 */
@Singleton
internal class ThymeleafReportServiceImpl(
    private val reportTaskRepository: ReportTaskRepository,
    private val chartService: ChartService,
    private val templateBeanFactory: TemplateBeanFactory
) : TemplateReportService {

    override suspend fun generatePdf(
        report: ReportEntity,
        campaignReportDetail: CampaignReportDetail,
        reportTask: ReportTaskEntity,
        creator: String,
        dataSeries: Collection<DataSeriesEntity>,
        tenant: String,
        reportTempDir: Path
    ): ByteArray {
        reportTaskRepository.update(
            reportTask.copy(
                status = ReportTaskStatus.PROCESSING,
                updateTimestamp = Instant.now()
            )
        )
        val html = renderTemplate(campaignReportDetail, dataSeries, reportTempDir)
        return generatePdfFromHtml(html)
    }

    /**
     * Builds up the thymeleaf page with specified context.
     *
     * @param campaignReportDetail contains data for building thymeleaf page
     * @param dataSeries collection of DataSeriesEntity related to current report generation
     * @param reportTempDir absolute path of the temporal directory to store current report related files
     */
    @KTestable
    private suspend fun renderTemplate(
        campaignReportDetail: CampaignReportDetail,
        dataSeries: Collection<DataSeriesEntity>,
        reportTempDir: Path
    ): String {
        val templateEngine = templateBeanFactory.templateEngine()
        val chartImages = mutableListOf<String>()
        if (campaignReportDetail.chartData.isNotEmpty()) {
            campaignReportDetail.chartData.mapIndexed { index, chartData ->
                if (chartData.isNotEmpty()) {
                    chartImages.add(
                        Files.readAllBytes(
                            chartService.buildChart(
                                chartData,
                                dataSeries,
                                index,
                                reportTempDir
                            )
                        ).toBase64()
                    )
                }
            }
        }
        val fontUrl = loadFont(reportTempDir)
        val context = buildContext(campaignReportDetail, chartImages, fontUrl)

        return templateEngine.process("report-template", context)
    }

    /**
     * Builds up the thymeleaf context.
     *
     * @param campaignReportDetail contains data with which to set up the context
     * @param chartImages collection of base64 encoded strings of chart image files
     */
    @KTestable
    private fun buildContext(
        campaignReportDetail: CampaignReportDetail,
        chartImages: List<String>,
        fontUrl: String
    ): Context {
        val assets = readResources()
        return Context().apply {
            // Configure the report title.
            setVariable("title", campaignReportDetail.reportName)
            // Configure data to populate the campaign summary section.
            setVariable("campaigns", campaignReportDetail.campaignData)
            val eventTableData = mutableListOf<Collection<TimeSeriesRecord>>()
            val meterTableData = mutableListOf<Collection<TimeSeriesRecord>>()
            campaignReportDetail.tableData.forEach { timeSeriesRecords ->
                when (timeSeriesRecords.first()) {
                    is TimeSeriesEvent -> eventTableData.add(timeSeriesRecords)
                    is TimeSeriesMeter -> meterTableData.add(timeSeriesRecords)
                }
            }
            // Configure data to populate TimeSeriesEvent tables.
            setVariable("eventTableData", eventTableData.ifEmpty { null })
            // Configure data to populate TimeSeriesMeter tables.
            setVariable("meterTableData", meterTableData.ifEmpty { null })
            // Configure data to populate report charts.
            setVariable("chartImages", chartImages)
            // Configure the path to read the font family.
            setVariable("fontUrl", fontUrl)
            // Configure the path for the time svg icon.
            setVariable("timeImage", assets["timeImage"])
            // Configure the path for the user svg icon.
            setVariable("userImage", assets["userImage"])
            // Configure the path for the css styles.
            setVariable("stylePath", assets["stylePath"])
        }
    }

    /**
     * Generates a pdf template from HTML string.
     *
     * @param html HTML template to be rendered
     */
    @KTestable
    private suspend fun generatePdfFromHtml(html: String): ByteArray {
        try {
            require(html.isNotEmpty()) { "HTML File is empty" }
            val document = Jsoup.parse(html, CHARACTER_ENCODING)
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
            val iTextRenderer = ITextRenderer()
            val outputStream = ByteArrayOutputStream()
                iTextRenderer.apply {
                    val sharedContext = sharedContext
                    sharedContext.isPrint = true
                    sharedContext.isInteractive = false
                    // @FIXME This should help with the image rendering but it throws an exception.
                    sharedContext.replacedElementFactory =
                        MediaReplacedElementFactory(sharedContext.replacedElementFactory)
                    setDocumentFromString(document.html())
                    layout()
                }
            outputStream.use(iTextRenderer::createPDF)
            val fileContent = outputStream.toByteArray()
            assert(fileContent.isNotEmpty()) { "File content could not be read" }
            return fileContent
        } catch (e: Exception) {
            throw IllegalArgumentException(e.message)
        }
    }

    /**
     * Reads the font file from the classpath to a temporal directory and returns
     * the path to be used in thymeleaf template.
     */
    @KTestable
    private fun loadFont(reportTempDir: Path): String {
        val destinationPath = Files.createTempFile(reportTempDir, "outfit", ".ttf")
        val fontInputStream = javaClass.classLoader.getResourceAsStream("public/assets/outfit-font.ttf")
        fontInputStream?.let {
            Files.copy(fontInputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        }

        return destinationPath.absolutePathString()
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    /**
     * Loads the css and image resources and writes them to a temporal directory.
     */
    private fun readResources(): Map<String, String> {
        val reportTempDir = Files.createTempDirectory("assets").toAbsolutePath()
        val timePath = Files.createTempFile(reportTempDir, "time", ".svg")
        val userPath = Files.createTempFile(reportTempDir, "user", ".svg")
        val cssPath = Files.createTempFile(reportTempDir, "style", ".css")
        val timeInputStream = javaClass.classLoader.getResourceAsStream("public/images/time.svg")
        val userInputStream = javaClass.classLoader.getResourceAsStream("public/images/user.svg")
        val cssInputStream = javaClass.classLoader.getResourceAsStream("public/style.css")
        timeInputStream?.let {
            Files.copy(timeInputStream, timePath, StandardCopyOption.REPLACE_EXISTING)
        }
        userInputStream?.let {
            Files.copy(userInputStream, userPath, StandardCopyOption.REPLACE_EXISTING)
        }
        cssInputStream?.let {
            Files.copy(cssInputStream, cssPath, StandardCopyOption.REPLACE_EXISTING)
        }

        return mapOf(
            "timeImage" to timePath.absolutePathString(),
            "userImage" to userPath.absolutePathString(),
            "stylePath" to cssPath.absolutePathString(),
        )
    }

    private companion object {
        const val CHARACTER_ENCODING = "UTF-8"
    }
}