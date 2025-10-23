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
class LineStyleGenerator {

    private lateinit var lineStore: List<BasicStroke>

    init {

        //Medium dash-gap pattern.
        val basicDash = floatArrayOf(10f, 5f)
        // Long-short alternating pattern.// medium dash-gap pattern
        val aaDash = floatArrayOf(30f, 10f, 5f, 10f)
        // Dotted line (short dot, long gap).
        val dot = floatArrayOf(2f, 6f)
        // Two short dashes + one long.
        val twoDash = floatArrayOf(20f, 5f, 5f, 5f, 20f, 5f)
        // Long-short-long-short repeating.
        val longShortDash = floatArrayOf(25f, 5f, 10f, 5f)
        // Thin solid line.
        val solidStroke = BasicStroke(0.8f)
        val dashed = BasicStroke(
            0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, basicDash, 0f
        )
        val dotted = BasicStroke(
            1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dot, 0f
        )
        val aa = BasicStroke(
            0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, aaDash, 0f
        )
        val twoDashes = BasicStroke(
            0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, twoDash, 0f
        )
        val longShort = BasicStroke(
            0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, longShortDash, 0f
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