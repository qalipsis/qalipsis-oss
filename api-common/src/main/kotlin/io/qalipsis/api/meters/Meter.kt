/*
 * Copyright 2023 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.meters

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.report.ReportMessageSeverity
import io.micrometer.core.instrument.Meter as MicrometerMeter

/**
 * Representation of a meter from [CampaignMeterRegistry].
 */
interface Meter<SELF : Meter<SELF>> : MicrometerMeter {

    val id: Id

    /**
     * Configures the way the meter should be reported next to the step details.
     * Only the first call is taken into account, further ones are ignored.
     */
    fun report(configure: ReportingConfiguration<SELF>.() -> Unit): SELF

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
            toNumber: T.() -> Number
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
            toNumber: T.() -> Number
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
        val campaignKey: CampaignKey,
        val scenarioName: ScenarioName,
        val stepName: StepName,
        val meterName: String,
        val tags: Map<String, String>
    )

}