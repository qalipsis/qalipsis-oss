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
import io.micronaut.core.io.ResourceLoader
import io.qalipsis.api.logging.LoggerHelper.logger
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
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.io.path.absolutePathString


/**
 * Custom implementation of [TemplateReportService] to build report template.
 *
 * @author Francisca Eze
 */
@Singleton
class ThymeleafReportServiceImpl(
    private val reportTaskRepository: ReportTaskRepository,
    private val chartService: ChartService,
    private val templateBeanFactory: TemplateBeanFactory,
    private val resourceLoader: ResourceLoader,
) : TemplateReportService {

    /**
     * The CSS content to be used in the report template.
     *
     * Initialized in [initCss] by loading `public/style.css` from the classpath
     * and converting its contents to a UTF-8 string.
     */
    private lateinit var cssContent: String

    /**
     * List of report icon names and their corresponding file names.
     */
    private val reportIcons = listOf(
        "userImage" to "user.svg",
        "timeImage" to "time.svg",
        "logoImage" to "logo.svg",
        "circleCheck" to "circleCheck.svg",
        "rocket" to "rocket.svg",
        "triangle" to "triangle.svg",
        "shield" to "shield.svg",
        "redTriangle" to "redTriangle.svg",
        "notification" to "notification.svg"
    )

    @PostConstruct
    fun initCss() {
        val cssInputStream = javaClass.classLoader.getResourceAsStream("public/style.css")
            ?: throw IllegalArgumentException("CSS file not found in classpath.")
        cssInputStream.use { input ->
            cssContent = input.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    override suspend fun generatePdf(
        report: ReportEntity,
        campaignReportDetail: CampaignReportDetail,
        reportTask: ReportTaskEntity,
        creator: String,
        dataSeries: Collection<DataSeriesEntity>,
        tenant: String,
        reportTempDir: Path,
        campaignReferenceToName: Map<String, String>
    ): ByteArray {
        reportTaskRepository.update(
            reportTask.copy(
                status = ReportTaskStatus.PROCESSING,
                updateTimestamp = Instant.now()
            )
        )
        val html = renderTemplate(campaignReportDetail, dataSeries, reportTempDir, campaignReferenceToName)

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
        reportTempDir: Path,
        campaignReferenceToName: Map<String, String>
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
                                reportTempDir,
                                campaignReferenceToName
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
     * @param fontUrl absolute string path of the font
     */
    @KTestable
    private fun buildContext(
        campaignReportDetail: CampaignReportDetail,
        chartImages: List<String>,
        fontUrl: String
    ): Context {
        val assets = readResources()
        return Context().apply {
            // Configure styling.
            setVariable("css", cssContent)
            // Configure the report title.
            setVariable("title", campaignReportDetail.reportName)
            // Configure the report generation time.
            setVariable("generatedTime", Instant.now().atZone(ZoneOffset.UTC))
            setVariable("UTC", ZoneOffset.UTC)
            // Configure data to populate the campaign summary section.
            setVariable("campaigns", campaignReportDetail.campaignReportData)
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
            // Configure the data for the svg icons.
            reportIcons.forEach { (key, _) ->
                setVariable(key, assets[key])
            }
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
                sharedContext.replacedElementFactory =
                    MediaReplacedElementFactory(sharedContext.replacedElementFactory)
                setDocumentFromString(document.html())
                layout()
            }
            outputStream.use { iTextRenderer.createPDF(it) }
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
     * Reads image resources from the classpath and converts them to base64 encoded strings.
     */
    private fun readResources(): Map<String, String> {
        val resourceMap = mutableMapOf<String, String>()
        try {
            //Load the image icons.
            reportIcons.forEach { (key, path) ->
                val bytes = resourceLoader.getResourceAsStream("classpath:$BASE_IMAGE_PATH/$path").get().readBytes()
                resourceMap[key] = Base64.getEncoder().encodeToString(bytes)
            }
        } catch (ex: Exception) {
            logger.debug { "Not able to load resource: $ex" }
        }

        return resourceMap
    }

    /**
     * Contains constants used within this class.
     *
     * @property logger custom logger instance
     * @property CHARACTER_ENCODING specifies the character encoding system to be used
     * @property CSS_STYLE_PATH path to the location of the css styles
     */
    private companion object {

        val logger = logger()

        const val CHARACTER_ENCODING = "UTF-8"

        const val BASE_IMAGE_PATH = "public/images"

        const val CSS_STYLE_PATH = "public/style.css"
    }
}