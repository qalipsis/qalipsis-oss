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

package io.qalipsis.core.head.inmemory.consolereporter

import com.varabyte.kotter.foundation.text.red
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.yellow
import com.varabyte.kotter.runtime.render.RenderScope
import com.varabyte.kotterx.grid.Cols
import com.varabyte.kotterx.grid.GridCharacters
import com.varabyte.kotterx.grid.grid
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Reporter to display the live metrics of a running campaign.
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.STANDALONE]),
    Requires(
        property = "report.export.console-live.enabled",
        notEquals = StringUtils.FALSE,
        defaultValue = StringUtils.TRUE
    )
)
internal class ConsoleMeterReporter : MeterReporter {

    private val meters =
        ConcurrentHashMap<ScenarioName, MutableMap<StepName, MutableMap<Short, MutableMap<Short, MutableList<ReportedValue<*>>>>>>()

    override fun <T : Meter<*>> report(
        meter: T,
        format: String,
        severity: Number.() -> ReportMessageSeverity,
        row: Short,
        column: Short,
        toNumber: T.() -> Number
    ) {
        meters.computeIfAbsent(meter.id.scenarioName) { ConcurrentHashMap() }
            .computeIfAbsent(meter.id.stepName) { ConcurrentHashMap() }.apply {
                log.trace { "Logging the " }
                if (size > 10) {
                    throw RuntimeException("The step ${meter.id.stepName} has too many rows: $size")
                }
            }
            .computeIfAbsent(row) { ConcurrentHashMap() }.apply {
                if (size > 10) {
                    throw RuntimeException("The step ${meter.id.stepName} has too many columns for row $row: $size")
                }
            }
            .computeIfAbsent(column) { concurrentList() }.apply {
                if (size > 4) {
                    throw ConsoleException("The step ${meter.id.stepName} has too many values at console position ${row}:${column}. Meters: ${map { it.meter.id }}")
                }
                add(ReportedValue(meter, format, severity, toNumber))
            }
    }

    /**
     * Displays the meters for the provided step in the console.
     */
    @Suppress("UNCHECKED_CAST")
    fun renderStepMeters(
        renderScope: RenderScope,
        scenarioName: ScenarioName,
        stepName: StepName,
        renderWidth: Int
    ) {
        if (!stepName.startsWith('_')) {
            meters[scenarioName]?.get(stepName)?.toSortedMap()?.apply {
                val colsCount = values.maxOf { it.values.size }
                val cellWidth = (renderWidth / colsCount) - 2 // Remove two spaces for the borders.
                with(renderScope) {
                    grid(
                        Cols { repeat(colsCount) { fixed(width = cellWidth - 1) } },
                        characters = GridCharacters.CURVED,
                        paddingLeftRight = 0,
                        //targetWidth = renderWidth - colsCount * 2 // Remove two spaces by cell for the borders.
                    ) {
                        forEach { (rowIndex, columns) ->
                            columns.forEach { (columnIndex, cellValues) ->
                                cell(row = rowIndex.toInt(), col = columnIndex.toInt()) {
                                    cellValues.forEach { (meter, format, severity, toNumber) ->
                                        tryAndLog(log) {
                                            val toNumberBlock = toNumber as (Meter<*>.() -> Number)
                                            val value = meter.toNumberBlock().toDouble()
                                            val text = String.format(format, value)
                                            when (value.severity()) {
                                                ReportMessageSeverity.ERROR -> red { text(text) }
                                                ReportMessageSeverity.ABORT -> red { text(text) }
                                                ReportMessageSeverity.WARN -> yellow { text(text) }
                                                else -> text(text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    override fun clean() {
        meters.clear()
    }

    /**
     * Value of [Meter] to report.
     */
    private data class ReportedValue<T : Meter<*>>(
        val meter: T,
        val format: String,
        val severity: Number.() -> ReportMessageSeverity,
        val toNumber: T.() -> Number
    ) : Comparable<ReportedValue<*>> {

        override fun compareTo(other: ReportedValue<*>): Int {
            return format.compareTo(other.format)
        }
    }

    private companion object {

        val log = logger()

    }

}