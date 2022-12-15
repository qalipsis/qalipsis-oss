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

import jakarta.inject.Singleton
import java.awt.BasicStroke

/**
 * Use different line properties and configuration to define styles of a line.
 *
 * @author Francisca Eze
 */
@Singleton
internal class LineStyleGenerator {

    private lateinit var lineStore: List<BasicStroke>

    init {
        val basicDash = floatArrayOf(8.0f, 8.0f)
        val aaDash = floatArrayOf(35f, 25f, 35f, 25f)
        val dot = floatArrayOf(1f, 0f, 1f)
        val twoDash = floatArrayOf(15f, 15f, 5f, 15f, 15f)
        val longShortDash = floatArrayOf(20.0f, 5.0f, 3.0f, 5.0f)
        val solidStroke = BasicStroke(1.0f)
        val dashed = BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, basicDash, 0f
        )
        val dotted = BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, dot, 0f
        )
        val aa = BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, aaDash, 0f
        )
        val twoDashes = BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, twoDash, 0f
        )
        val longShort = BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, longShortDash, 0f
        )

        lineStore = listOf(solidStroke, dashed, dotted, aa, twoDashes, longShort)
    }

    /**
     * Returns a [BasicStroke] that matches the provided index.
     *
     * @param index index of a particular stroke in the lineStore
     */
    fun getLineStyle(index: Int) = lineStore.getOrElse(index) { BasicStroke(1.0f) }
}