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

package io.qalipsis.core.head.report.chart

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.lang.alsoWhenNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import jakarta.inject.Singleton
import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.chart.LegendItem
import org.jfree.chart.LegendItemCollection
import org.jfree.chart.LegendItemSource
import org.jfree.chart.StandardChartTheme
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.block.BlockBorder
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYSplineRenderer
import org.jfree.chart.title.LegendTitle
import org.jfree.data.xy.XYSeries
import org.jfree.svg.SVGGraphics2D
import org.jfree.svg.SVGUtils
import org.jfree.ui.RectangleEdge
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random


/**
 * Custom implementation of [ChartService] to plot time-series charts.
 *
 * @author Francisca Eze
 */
@Singleton
class ChartServiceImpl(private val lineStyleGenerator: LineStyleGenerator) : ChartService {

    override fun buildChart(
        data: Map<String, List<TimeSeriesAggregationResult>>,
        dataSeries: Collection<DataSeriesEntity>,
        index: Int,
        reportTempDir: Path,
    ): Path {
        require(data.isNotEmpty()) { "Cannot generate chart with empty data" }
        val dataSeriesDrawingConfigurations = mutableMapOf<String, DataSeriesDrawingConfiguration>()
        val campaignKeyToLineMap = mutableMapOf<String, Int>()
        // Re-arrange the data to build time series pairs and other data structures needed to render and style the charts.
        buildDataSet(data, campaignKeyToLineMap, dataSeriesDrawingConfigurations)
        // Map each dataSeries reference to a unique color.
        val dataSeriesEntityMap = dataSeries.associateBy { dataSeriesEntity -> dataSeriesEntity.reference }
        mapDataSeriesToColour(dataSeriesEntityMap, dataSeriesDrawingConfigurations)
        // Plot datasets, configure renderers and styling to the X and Y axes of the chart.
        val plot = plotChart(XYPlot(), dataSeriesDrawingConfigurations, campaignKeyToLineMap)
        // Create a chart instance using the specified plot configurations.
        val chart = createChart(plot, dataSeriesDrawingConfigurations)
        // Initialize temporary file location for the report file.
        val imageFilePath = Files.createTempFile(reportTempDir, "requestChart${index + 1}", ".svg")
        val svgGraphics = SVGGraphics2D(rectangleWidth, rectangleHeight)
        val rectangle = Rectangle(0, 0, rectangleWidth.toInt(), rectangleHeight.toInt())
        // Draw the chart on a Java 2D graphics device with the configured rectangle width and height.
        chart.draw(svgGraphics, rectangle)
        // Write the chart to an SVG file.
        SVGUtils.writeToSVG(imageFilePath.toFile(), svgGraphics.svgElement)

        return imageFilePath
    }


    /**
     * Re-arrange the data to build [XYSeries] and other data structures
     * needed to generate and style the charts.
     *
     * @param data map of dataSeries references to the [TimeSeriesAggregationResult]
     * @param campaignKeyToLineMap campaign keys mapped to their respective line indexes
     * @param dataSeriesDrawingConfigurations dataseries references each mapped to its drawing configurations
     */
    @KTestable
    private fun buildDataSet(
        data: Map<String, List<TimeSeriesAggregationResult>>,
        campaignKeyToLineMap: MutableMap<String, Int>,
        dataSeriesDrawingConfigurations: MutableMap<String, DataSeriesDrawingConfiguration>,
    ) {
        logger.info { "Starting chart generation" }
        var colorIndex = 0
        var campaignLineIndex = 0
        data.forEach { (dataSeriesKey, timeSeriesAggregationResults) ->
            // Save the index of each data series entry keyed by the data series reference.
            // Build the xySeriesMap which stores xySeries keyed to their concatenated campaignKey and data series references.
            // The key is used in setting a renderer on a particular index for each dataSeries.
            // Retrieve a pre-existing drawing configuration or return a new instance.
            val dataSeriesDrawingConfiguration =
                dataSeriesDrawingConfigurations.getOrDefault(dataSeriesKey, DataSeriesDrawingConfiguration(-1))
            dataSeriesDrawingConfiguration.seriesIndex = colorIndex++
            // Update the dataSeriesDrawingConfiguration store.
            dataSeriesDrawingConfigurations[dataSeriesKey] = dataSeriesDrawingConfiguration
            // Map of unique time series sets, used in further generation of the dataset collection.
            val xySeriesMap = mutableMapOf<PlotCompositeKey, XYSeries>()
            timeSeriesAggregationResults.forEach { timeSeriesAggregationResult ->
                val campaignKey = timeSeriesAggregationResult.campaign!!
                // Create a key identifier for each unique series.
                val plotCompositeKey = PlotCompositeKey(campaignKey, dataSeriesKey)
                // Create new [XYSeries] or get a pre-existing series and add new key-value pairs.
                val xySeries = xySeriesMap.getOrDefault(plotCompositeKey, XYSeries(plotCompositeKey.compositeKey))
                    .also { it.add(timeSeriesAggregationResult.elapsed.toNanos(), timeSeriesAggregationResult.value) }
                xySeriesMap[plotCompositeKey] = xySeries
                // Store each unique campaign key to an index to be used in assigning line styles per campaign key.
                campaignKeyToLineMap[campaignKey].alsoWhenNull {
                    campaignKeyToLineMap[campaignKey] = campaignLineIndex++
                }
            }
            // Create different datasets collection by dataSeries reference from the key-value batches
            // of the [XYSeries] created above.
            createDatasetCollection(xySeriesMap, dataSeriesDrawingConfigurations)
        }
    }

    /**
     * Creates a dataset collection, keyed by their unique dataSeries reference, from the batches of the
     * time series execution time mapped to their corresponding values.
     *
     * @param xySeriesMap map of unique time series sets, grouped by their campaignKeys and dataSeries
     * references and keyed to their dataSeries References. Used in generating the dataset collection
     * @param dataSeriesDrawingConfigurations dataseries references each mapped to its drawing configurations
     */
    @KTestable
    private fun createDatasetCollection(
        xySeriesMap: Map<PlotCompositeKey, XYSeries>,
        dataSeriesDrawingConfigurations: MutableMap<String, DataSeriesDrawingConfiguration>,
    ) {
        xySeriesMap.forEach { (xySeriesKey, xySeriesEntry) ->
            // Split the series key to get the campaign key and dataSeries reference.
            val campaignKey = xySeriesKey.campaignKey
            val dataSeriesKey = xySeriesKey.dataSeriesReference
            // Retrieve a pre-existing dataset collection by dataSeries reference from the xySeriesCollectionStore or return a new one.
            val dataSeriesDrawingConfiguration = dataSeriesDrawingConfigurations[dataSeriesKey]!!
            val xySeriesCollection = dataSeriesDrawingConfiguration.dataSet
            // Retrieve a pre-existing map of campaign keys to index or a new one.
            // This is used to keep track of the position of unique campaign keys in each series of the dataset collection.
            // This is subsequently used in assigning line styles and colours.
            val dataSeriesDrawingIndicesByCampaign = dataSeriesDrawingConfiguration.dataSeriesDrawingIndicesByCampaign
            // Store the index of series for unique campaign keys in an xySeriesCollection.
            dataSeriesDrawingIndicesByCampaign[campaignKey] = xySeriesCollection.seriesCount
            // Add modified series to dataset collection.
            xySeriesCollection.addSeries(xySeriesEntry)
            dataSeriesDrawingConfiguration.dataSet = xySeriesCollection
            // Store updated values in dataSeriesToCampaignKeyIndexMap.
            dataSeriesDrawingConfiguration.dataSeriesDrawingIndicesByCampaign = dataSeriesDrawingIndicesByCampaign
            // Store the updated collection after update.
            dataSeriesDrawingConfigurations[dataSeriesKey] = dataSeriesDrawingConfiguration
        }
    }

    /**
     * Plot datasets, configure renderers and styling to the X and Y axes of the chart.
     *
     * @param xyPlot instance of [XYPlot] to represent chart data
     * @param dataSeriesDrawingConfigurations dataseries references each mapped to its drawing configurations
     * @param campaignKeyToLineMap campaign keys mapped to their respective line indexes
     */
    @KTestable
    private fun plotChart(
        xyPlot: XYPlot,
        dataSeriesDrawingConfigurations: Map<String, DataSeriesDrawingConfiguration>,
        campaignKeyToLineMap: Map<String, Int>,
    ): XYPlot {
        dataSeriesDrawingConfigurations.forEach { (_, dataSeriesDrawingConfiguration) ->
            val seriesIndex = dataSeriesDrawingConfiguration.seriesIndex
            // Set series collection in the plot accordingly: each index to the exact time series set that belongs to it.
            xyPlot.setDataset(seriesIndex, dataSeriesDrawingConfiguration.dataSet)
            // Customize the plot with renderers and axis.
            // Initialize a renderer for each collection.
            val splineRenderer = XYSplineRenderer()
            // Loop through the data series reference to campaignKey to index map.
            dataSeriesDrawingConfiguration.dataSeriesDrawingIndicesByCampaign.forEach { campaignEntry ->
                // Get the series index per campaign.
                val xySeriesIndex = campaignEntry.value
                splineRenderer.apply {
                    // Use the data series reference to get the colours and apply the color on target series indexes.
                    setSeriesPaint(xySeriesIndex, dataSeriesDrawingConfiguration.color)
                    // Use the campaign key of each entry to get unique line styles and apply the styles on target series indexes.
                    setSeriesStroke(
                        xySeriesIndex,
                        campaignKeyToLineMap[campaignEntry.key]?.let { lineStyleGenerator.getLineStyle(it) })
                    // Remove shapes on intersection points of graph lines.
                    setSeriesShapesVisible(xySeriesIndex, false)
                }
            }
            // Use the index of the dataSeries to set the corresponding renderer in the plot.
            xyPlot.setRenderer(seriesIndex, splineRenderer)
            // Set range axes values and colors.
            val colour = dataSeriesDrawingConfiguration.color
            val rangeAxis = NumberAxis().apply {
                tickLabelPaint = colour
                labelPaint = colour
                axisLinePaint = colour
            }
            xyPlot.setRangeAxis(seriesIndex, rangeAxis)
            // Map different dataset to unique axes.
            xyPlot.mapDatasetToRangeAxis(seriesIndex, seriesIndex)
        }
        // Set domain axes values and colors.
        xyPlot.apply {
            isDomainGridlinesVisible = false
            domainAxis = DateAxis("Execution time").apply {
                labelFont = Font(FONT_FAMILY, Font.PLAIN, 10)
                lowerMargin = 0.08
            }
            outlinePaint = null
        }

        return xyPlot
    }

    /**
     * Maps each drawing configuration to its respective colors.
     *
     * @param dataSeriesMap map of [DataSeriesEntity] keyed by their references.
     * @param dataSeriesDrawingConfigurations map of [DataSeriesDrawingConfiguration] keyed by their dataSeries references.
     */
    @KTestable
    private fun mapDataSeriesToColour(
        dataSeriesMap: Map<String, DataSeriesEntity>,
        dataSeriesDrawingConfigurations: MutableMap<String, DataSeriesDrawingConfiguration>,
    ) {
        dataSeriesDrawingConfigurations.forEach { (dataSeriesKey, dataSeriesDrawingConfiguration) ->
            val dataSeriesEntity = dataSeriesMap[dataSeriesKey]
            val seriesColor = dataSeriesEntity?.color?.let {
                hexToColor(it, dataSeriesEntity.colorOpacity)
            } ?: generateRandomColour().darker()
            dataSeriesDrawingConfiguration.apply {
                color = seriesColor
            }
        }
    }

    /**
     * Converts a hex string to a color or null if it can't be converted.
     *
     * @param colorHex the hex color (e.g #CCCCCCFF or #CCCCCC)
     * @param opacity the percentage value for opacity
     */
    @KTestable
    private fun hexToColor(colorHex: String, opacity: Int?): Color? {
        val hex = colorHex.replace("#", "")
        return try {
            Color(
                Integer.valueOf(hex.substring(0, 2), 16),
                Integer.valueOf(hex.substring(2, 4), 16),
                Integer.valueOf(hex.substring(4, 6), 16),
                (((opacity ?: 100).toFloat() / 100) * 255).toInt()
            )
        } catch (e: Exception) {
            logger.warn { "Unable to convert $colorHex to a valid color type" }
            null
        }
    }

    /**
     * Creates a chart instance using the specified plot configurations.
     *
     * @param plot instance of [XYPlot] representing data in the form of (x, y) pairs.
     * @param dataSeriesDrawingConfigurations map of [DataSeriesDrawingConfiguration] keyed by their dataSeries references.
     */
    private fun createChart(
        plot: XYPlot,
        dataSeriesDrawingConfigurations: MutableMap<String, DataSeriesDrawingConfiguration>,
    ): JFreeChart {
        ChartFactory.setChartTheme(buildChartTheme())
        val chart = JFreeChart(
            "", Font(FONT_FAMILY, Font.BOLD, 14), plot, true,
        ).apply { backgroundPaint = Color.white }

        return createAndCustomizeLegend(chart, dataSeriesDrawingConfigurations)
    }

    /**
     * Configures the chart attributes for the chart instance.
     */
    private fun buildChartTheme(): StandardChartTheme {
        //FIXME Reading the specified font file, public/assets/outfit-font.ttf, always returns more bytes than its
        // original file. The font path can be passed in from the build chart method above
        var font = Font("Tahoma", Font.PLAIN, 11)
        try {
            javaClass.getResourceAsStream("public/assets/outfit-font.ttf")?.let {
                font = Font.createFont(Font.TRUETYPE_FONT, BufferedInputStream(it))
            }
        } catch (ex: Exception) {
            logger.warn { "Font cannot be created: ${ex.message}" }
        }
        return StandardChartTheme(FONT_FAMILY).apply {
            regularFont = font.deriveFont(12f)
            smallFont = font.deriveFont(10f)
        }
    }

    /**
     * Creates the dataSeries legend and customize the default legend.
     *
     * @param chart instance of [JFreeChart]
     * @param dataSeriesDrawingConfigurations map of [DataSeriesDrawingConfiguration] keyed by their dataSeries references.
     */
    private fun createAndCustomizeLegend(
        chart: JFreeChart,
        dataSeriesDrawingConfigurations: MutableMap<String, DataSeriesDrawingConfiguration>,
    ): JFreeChart {
        chart.legend.apply {
            frame = BlockBorder(Color(218, 225, 225))
            itemPaint = Color(6, 24, 26)
            itemFont = Font(FONT_FAMILY, Font.PLAIN, 10)
        }
        val source = LegendItemSource {
            val legendItemCollection = LegendItemCollection()
            dataSeriesDrawingConfigurations.forEach { (dataSeriesReference, dataSeriesDrawingConfiguration) ->
                legendItemCollection.add(LegendItem(dataSeriesReference, dataSeriesDrawingConfiguration.color))
            }
            legendItemCollection
        }
        return chart.apply {
            addLegend(LegendTitle(source))
            getLegend(0).apply { position = RectangleEdge.BOTTOM }
            getLegend(1).apply { position = RectangleEdge.TOP }
        }
    }

    /**
     * Generates random color when that of the dataSeries isn't specified.
     */
    private fun generateRandomColour() = Color.getHSBColor(
        (Random().nextFloat() / 2f + 0.5).toFloat(),
        (Random().nextFloat() / 2f + 0.5).toFloat(),
        (Random().nextFloat() / 2f + 0.5).toFloat()
    )

    private companion object {
        private const val FONT_FAMILY = "Outfit"

        private const val rectangleWidth = 935.0

        private const val rectangleHeight = 320.0

        private val logger = logger()
    }
}