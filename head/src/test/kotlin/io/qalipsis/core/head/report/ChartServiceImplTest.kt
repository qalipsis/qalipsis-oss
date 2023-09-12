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
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockkStatic
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.report.chart.ChartServiceImpl
import io.qalipsis.core.head.report.chart.DataSeriesDrawingConfiguration
import io.qalipsis.core.head.report.chart.LineStyleGenerator
import io.qalipsis.core.head.report.chart.catadioptre.buildDataSet
import io.qalipsis.core.head.report.chart.catadioptre.mapDataSeriesToColour
import io.qalipsis.core.head.report.chart.catadioptre.plotChart
import io.qalipsis.test.mockk.WithMockk
import org.jfree.chart.plot.XYPlot
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.Font
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

@WithMockk
internal class ChartServiceImplTest {

    private val lineStyleGenerator = LineStyleGenerator()

    @InjectMockKs
    private lateinit var chartService: ChartServiceImpl

    private val aggregationResult = mapOf(
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
    private val dataSeriesEntity = DataSeriesEntity(
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

    @Test
    fun `should build chart given valid data and dataSeries`() {
        //given
        val dataSeries = listOf(
            dataSeriesEntity,
            dataSeriesEntity.copy(reference = "data-series-2", color = null, colorOpacity = 23),
            dataSeriesEntity.copy(reference = "data-series-3", color = null, colorOpacity = null),
            dataSeriesEntity.copy(reference = "data-series-4", color = "#000000", colorOpacity = 50),
        )
        val reportDir = Files.createTempDirectory("test").toAbsolutePath()

        //when
        val plot = chartService.buildChart(aggregationResult, dataSeries, 1, reportDir)

        //then
        val content = String(Files.readAllBytes(plot), StandardCharsets.UTF_8)
        assertNotNull(content)
        assertTrue(content.contains("Execution time"))
        assertTrue(content.contains("campaign-1/data-series-1"))

    }

    @Test
    fun `should generate chart when data is not empty but dataSeriesEntity is empty`() {
        //given
        val reportDir = Files.createTempDirectory("test").toAbsolutePath()

        //when
        val plot = chartService.buildChart(aggregationResult, emptyList(), 1, reportDir)

        //then
        val content = String(Files.readAllBytes(plot), StandardCharsets.UTF_8)
        assertNotNull(content)
        assertTrue(content.contains("Execution time"))
        assertTrue(content.contains("campaign-1/data-series-1"))
    }

    @Test
    fun `should not generate chart when data is empty`() {
        //given
        val reportDir = Files.createTempDirectory("test").toAbsolutePath()
        val dataSeries = listOf(
            dataSeriesEntity,
            dataSeriesEntity.copy(reference = "data-series-2", color = null, colorOpacity = 23),
            dataSeriesEntity.copy(reference = "data-series-3", color = null, colorOpacity = null),
            dataSeriesEntity.copy(reference = "data-series-4", color = "#000000", colorOpacity = 50),
        )

        //when
        val caught = assertThrows<IllegalArgumentException> {
            chartService.buildChart(emptyMap(), dataSeries, 1, reportDir)
        }

        // then
        assertThat(caught).all {
            prop(IllegalArgumentException::message).isEqualTo("Cannot generate chart with empty data")
        }
    }

    @Test
    fun `should populate the drawingConfiguration when buildDataSet is triggered`() {
        //given
        val dataSeriesDrawingConfigurations = mutableMapOf<String, DataSeriesDrawingConfiguration>()
        val campaignKeyToLineMap = mutableMapOf<String, Int>()

        //when
        chartService.buildDataSet(aggregationResult, campaignKeyToLineMap, dataSeriesDrawingConfigurations)

        //then
        assertThat(dataSeriesDrawingConfigurations.size).isEqualTo(4)
        assertThat(campaignKeyToLineMap.size).isEqualTo(5)
        assertThat(dataSeriesDrawingConfigurations["data-series-1"]).isEqualTo(DataSeriesDrawingConfiguration(0))
        assertThat(dataSeriesDrawingConfigurations["data-series-2"]).isEqualTo(DataSeriesDrawingConfiguration(1))
        assertThat(dataSeriesDrawingConfigurations["data-series-3"]).isEqualTo(DataSeriesDrawingConfiguration(2))
        assertThat(dataSeriesDrawingConfigurations["data-series-4"]).isEqualTo(DataSeriesDrawingConfiguration(3))
        assertThat(campaignKeyToLineMap["campaign-1"]).isEqualTo(0)
        assertThat(campaignKeyToLineMap["campaign-2"]).isEqualTo(1)
        assertThat(campaignKeyToLineMap["campaign-3"]).isEqualTo(2)
        assertThat(campaignKeyToLineMap["campaign-4"]).isEqualTo(3)
        assertThat(campaignKeyToLineMap["campaign-5"]).isEqualTo(4)
    }

    @Test
    fun `should populate a chart drawing configuration with the appropriate data series color`() {
        //given
        val opacity = 59
        val dataSeriesMap = mapOf(
            "data-series-1" to dataSeriesEntity,
            "data-series-2" to dataSeriesEntity.copy(
                reference = "data-series-2",
                color = "#800080",
                colorOpacity = opacity
            ),
            "data-series-3" to dataSeriesEntity.copy(reference = "data-series-3", color = null)
        )
        val dataSeriesDrawingConfiguration = mutableMapOf(
            "data-series-2" to DataSeriesDrawingConfiguration(seriesIndex = 0),
            "data-series-1" to DataSeriesDrawingConfiguration(seriesIndex = 1),
            "data-series-3" to DataSeriesDrawingConfiguration(seriesIndex = 3)
        )
        mockkStatic(Color::class)
        val color = Color(0, 100, 0, 0)
        every { Color.getHSBColor(any<Float>(), any<Float>(), any<Float>()).darker() } returns color
        val alpha = ((opacity.toFloat() / 100) * 255).toInt()

        //when
        chartService.mapDataSeriesToColour(dataSeriesMap, dataSeriesDrawingConfiguration)

        //then
        assertThat(dataSeriesDrawingConfiguration["data-series-1"]).isNotNull().all {
            prop(DataSeriesDrawingConfiguration::seriesIndex).isEqualTo(1)
            prop(DataSeriesDrawingConfiguration::color).isEqualTo(Color(255, 0, 0))
        }
        assertThat(dataSeriesDrawingConfiguration["data-series-2"]).isNotNull().all {
            prop(DataSeriesDrawingConfiguration::seriesIndex).isEqualTo(0)
            prop(DataSeriesDrawingConfiguration::color).isEqualTo(Color(128, 0, 128, alpha))
        }
        assertThat(dataSeriesDrawingConfiguration["data-series-3"]).isNotNull().all {
            prop(DataSeriesDrawingConfiguration::seriesIndex).isEqualTo(3)
            prop(DataSeriesDrawingConfiguration::color).isEqualTo(Color(0, 100, 0, 0))
        }
    }

    @Test
    fun `should map the drawing configuration with random colors even when the dataSeries entity has no color configured`() {
        //given
        val dataSeriesDrawingConfiguration = mutableMapOf(
            "data-series-4" to DataSeriesDrawingConfiguration(seriesIndex = 0),
            "data-series-1" to DataSeriesDrawingConfiguration(seriesIndex = 1)
        )
        mockkStatic(Color::class)
        val color = Color(0, 100, 0, 0)
        val color2 = Color(0, 50, 0)
        every { Color.getHSBColor(any<Float>(), any<Float>(), any<Float>()).darker() } returns color andThen color2

        //when
        chartService.mapDataSeriesToColour(emptyMap(), dataSeriesDrawingConfiguration)

        //then
        assertThat(dataSeriesDrawingConfiguration["data-series-1"]).isNotNull().all {
            prop(DataSeriesDrawingConfiguration::seriesIndex).isEqualTo(1)
            prop(DataSeriesDrawingConfiguration::color).isEqualTo(color2)
        }
        assertThat(dataSeriesDrawingConfiguration["data-series-4"]).isNotNull().all {
            prop(DataSeriesDrawingConfiguration::seriesIndex).isEqualTo(0)
            prop(DataSeriesDrawingConfiguration::color).isEqualTo(color)
        }
    }

    @Test
    fun `should populate a given XYPlot when plotChart is called`() {
        //given
        val dataset1 = XYSeriesCollection()
        val xySeries = XYSeries("Campaign1-data-series-1")
        xySeries.add(0.45, 14577)
        xySeries.add(1.45, 48997)
        xySeries.add(1.45, 22577)
        val xySeries2 = XYSeries("Campaign2-data-series-2")
        xySeries.add(0.45, 4577)
        xySeries.add(1.45, 57997)
        xySeries.add(2.45, 45717)
        dataset1.addSeries(xySeries)
        dataset1.addSeries(xySeries2)
        val dataSeriesDrawingConfigurations = mapOf(
            "data-series-1" to DataSeriesDrawingConfiguration(seriesIndex = 0).apply {
                color = Color(0, 100, 0)
                dataSet = dataset1
                dataSeriesDrawingIndicesByCampaign = mutableMapOf("campaign-1" to 0, "campaign-2" to 1)
            },
            "data-series-2" to DataSeriesDrawingConfiguration(seriesIndex = 1).apply {
                color = Color(255, 100, 0)
                dataSet = XYSeriesCollection()
                dataSeriesDrawingIndicesByCampaign = mutableMapOf("campaign-3" to 0, "campaign-4" to 1)
            },
        )
        val campaignKeyToLineMap = mapOf("campaign-1" to 0, "campaign-2" to 1, "campaign-3" to 2)

        //when
        val plot = chartService.plotChart(XYPlot(), dataSeriesDrawingConfigurations, campaignKeyToLineMap)

        //then
        assertThat(plot.domainAxis.label).isEqualTo("Execution time")
        assertThat(plot.domainAxis.labelFont).isEqualTo(Font("Outfit", Font.PLAIN, 10))
        assertThat(plot.domainAxis.lowerMargin).isEqualTo(0.08)
        assertThat(plot.datasetCount).isEqualTo(2)
        assertThat(plot.getDataset(0)).isEqualTo(dataset1)
        assertThat(plot.getDataset(1)).isEqualTo(XYSeriesCollection())
        assertThat(plot.getRangeAxis(0).labelPaint).isEqualTo(Color(0, 100, 0))
        assertThat(plot.getRangeAxis(1).labelPaint).isEqualTo(Color(255, 100, 0))
        assertThat(plot.getRenderer(0).getSeriesPaint(0)).isEqualTo(Color(0, 100, 0))
        assertThat(plot.getRenderer(0).getSeriesStroke(0)).isEqualTo(lineStyleGenerator.getLineStyle(0))
        assertThat(plot.getRenderer(0).getSeriesPaint(1)).isEqualTo(Color(0, 100, 0))
        assertThat(plot.getRenderer(0).getSeriesStroke(1)).isEqualTo(lineStyleGenerator.getLineStyle(1))
        assertThat(plot.getRenderer(1).getSeriesPaint(0)).isEqualTo(Color(255, 100, 0))
        assertThat(plot.getRenderer(1).getSeriesStroke(0)).isEqualTo(lineStyleGenerator.getLineStyle(2))
    }
}