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

import io.micronaut.core.annotation.Introspected
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import java.awt.Color

/**
 * Data class to hold chart building and styling properties keyed by their dataSeries reference.
 *
 * @property seriesIndex index of a unique data series set in a collection
 * @property color [java.awt.Color] object containing the color property to assign series
 * @property dataSet collection of related(by data series reference) batches of [XYSeries]
 * @property dataSeriesDrawingIndicesByCampaign holds the campaignKey to series indices of the collection, to use in generating line-styles and colors
 *
 * @author Francisca Eze
 */
@Introspected
data class DataSeriesDrawingConfiguration(var seriesIndex: Int) {
    lateinit var color: Color
    var dataSet: XYSeriesCollection = XYSeriesCollection()
    var dataSeriesDrawingIndicesByCampaign: MutableMap<String, Int> = mutableMapOf()
}