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

package io.qalipsis.core.reporter

import io.qalipsis.api.meters.Meter
import io.qalipsis.api.report.ReportMessageSeverity

/**
 * Service in charge of reporting meters.
 */
interface MeterReporter {

    /**
     * Configures the reporting of [meter].
     *
     * @param meter the meter to report
     * @param format format of the display, respecting the rules of [String.format] - use %% if you need to display the percentage symbol, ex: "max %1$,.3f mb/s"
     * @param severity the function to calculate the severity depending on the value, to apply the convenient styling
     * @param row the row where the value should be displayed, empty rows are ignored
     * @param column the column where the value should be displayed - when several values are defined at the same column, they are ordered alphabetically and the higher is shifted to the right
     * @param toNumber operation to generate the number value to display
     */
    fun <T : Meter<*>> report(
        meter: T,
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short = 0,
        column: Short = 0,
        toNumber: T.() -> Number
    )

    /**
     * Cleans the configurations of [Meter]s to report.
     */
    fun clean()
}