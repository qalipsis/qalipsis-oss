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
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.foundation.text.yellow
import com.varabyte.kotter.runtime.render.RenderScope
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
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

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.STANDALONE]),
    Requires(property = "report.export.console-live.enabled", defaultValue = "false", value = "true")
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
            .computeIfAbsent(meter.id.stepName) { ConcurrentHashMap() }
            .computeIfAbsent(row) { ConcurrentHashMap() }
            .computeIfAbsent(column) { concurrentList() } += ReportedValue(meter, format, severity, toNumber)
    }

    /**
     * Displays the meters for the provided step in the console.
     */
    fun renderStepMeters(
        renderScope: RenderScope,
        scenarioName: ScenarioName,
        stepName: StepName,
        width: Int,
        indentation: String
    ) {
        meters[scenarioName]?.get(stepName)?.toSortedMap()?.values?.forEach { columns ->
            val columnSize = width / columns.size
            with(renderScope) {
                columns.toSortedMap().values.flatMap { reportedValue ->
                    reportedValue.sorted()
                }.forEach { (meter, format, severity, toNumber) ->
                    tryAndLog(log) {
                        val toNumberBlock = toNumber as Meter<*>.() -> Number
                        val value = meter.toNumberBlock().toDouble()
                        val text = ((indentation + String.format(format, value)).padEnd(columnSize))
                        when (value.severity()) {
                            ReportMessageSeverity.ERROR -> red { text(text) }
                            ReportMessageSeverity.ABORT -> red { text(text) }
                            ReportMessageSeverity.WARN -> yellow { text(text) }
                            else -> text(text)
                        }
                    }
                }
                textLine()
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