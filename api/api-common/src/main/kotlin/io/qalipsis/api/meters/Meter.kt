/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.meters

import io.qalipsis.api.report.ReportMessageSeverity
import java.time.Instant

/**
 * Representation of a meter from [CampaignMeterRegistry].
 */
interface Meter<SELF : Meter<SELF>> {

    val id: Id

    /**
     * Configures the way the meter should be reported next to the step details.
     * Only the first call is taken into account, further ones are ignored.
     */
    fun report(configure: ReportingConfiguration<SELF>.() -> Unit): SELF

    /**
     * Generates a snapshot representing the instantaneous state of the meter. Generating a snapshot
     * resets the instantaneous states.
     *
     * @return The snapshot that represents the instantaneous values of this meter.
     */
    suspend fun snapshot(timestamp: Instant): MeterSnapshot

    /**
     * Generates a snapshot representing the total state of the meter.
     *
     * @return The snapshot that represents the overall values of this meter.
     */
    suspend fun summarize(timestamp: Instant): MeterSnapshot

    interface ReportingConfiguration<T : Meter<*>> {

        /**
         * Configures the meter to be displayed in the report of the related scenario or step.
         * See the [official formatting documentation](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html).
         *
         * @param format format of the display, respecting the rules of [String.format] - use %% if you need to display the percentage symbol, ex: "max %,.3f mb/s" for a 3-digit precision decimal or "%,.0f req" for an integer.
         * @param severity the severity of the value, to apply the convenient styling
         * @param row the row where the value should be displayed, empty rows are ignored
         * @param column the column where the value should be displayed - when several values are defined at the same column, they are ordered alphabetically and the higher is shifted to the right
         * @param toNumber operation to generate the number value to display
         */
        fun display(
            format: String,
            severity: ReportMessageSeverity = ReportMessageSeverity.INFO,
            row: Short = 0,
            column: Short = 0,
            toNumber: T.() -> Number,
        ) = display(
            format,
            SEVERITY_MAPPERS[severity] ?: error("Unsupported value $severity"),
            row,
            column,
            toNumber
        )

        /**
         * Configures the meter to be displayed in the report of the related scenario or step.
         * See the [official formatting documentation](https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html).
         *
         * @param format format of the display, respecting the rules of [String.format] - use %% if you need to display the percentage symbol, ex: "max %1$,.3f mb/s"
         * @param severity the function to calculate the severity depending on the value, to apply the convenient styling
         * @param row the row where the value should be displayed, empty rows are ignored
         * @param column the column where the value should be displayed - when several values are defined at the same column, they are ordered alphabetically and the higher is shifted to the right
         * @param toNumber operation to generate the number value to display
         */
        fun display(
            format: String,
            severity: Number.() -> ReportMessageSeverity = { ReportMessageSeverity.INFO },
            row: Short = 0,
            column: Short = 0,
            toNumber: T.() -> Number,
        )

        companion object {

            private val SEVERITY_MAPPERS = mapOf<ReportMessageSeverity, Number.() -> ReportMessageSeverity>(
                ReportMessageSeverity.INFO to { ReportMessageSeverity.INFO },
                ReportMessageSeverity.WARN to { ReportMessageSeverity.WARN },
                ReportMessageSeverity.ABORT to { ReportMessageSeverity.ABORT },
                ReportMessageSeverity.ERROR to { ReportMessageSeverity.ERROR },
            )
        }

    }

    /**
     * Representation of the identifier of a [io.qalipsis.api.meters.Meter].
     */
    data class Id(
        val meterName: String,
        val type: MeterType,
        val tags: Map<String, String>,
    )
}