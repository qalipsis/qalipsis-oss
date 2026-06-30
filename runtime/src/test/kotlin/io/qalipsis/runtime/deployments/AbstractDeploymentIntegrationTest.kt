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

package io.qalipsis.runtime.deployments

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.runtime.test.JvmProcessUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Test class to validate the different modes of deployments.
 *
 * @author Eric Jessé
 */
abstract class AbstractDeploymentIntegrationTest {

    protected val client = HttpClient.newBuilder().build()

    protected val jvmProcessUtils = JvmProcessUtils()

    @AfterAll
    internal fun tearDownAll() {
        jvmProcessUtils.shutdown()
    }

    protected fun assertSuccessfulExecution(
        qalipsisProcess: JvmProcessUtils.ProcessDescriptor,
        before: Instant?,
        after: Instant?
    ) {
        Assertions.assertEquals(0, qalipsisProcess.process.exitValue())

        val scenarioReports = extractScenariosReports(qalipsisProcess.outputLines)
        assertThat(scenarioReports).all {
            hasSize(1)
            index(0).all {
                prop(ScenarioReport::scenarioName).isEqualTo("deployment-test")
                prop(ScenarioReport::start).isBetween(before, after)
                prop(ScenarioReport::end).isNotNull().isBetween(before, after)
                prop(ScenarioReport::duration).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.between(before, after))
                }
                prop(ScenarioReport::startedMinions).isEqualTo(500)
                prop(ScenarioReport::completedMinions).isEqualTo(500)
                prop(ScenarioReport::successfulStepExecutions).isEqualTo(1_500)
                prop(ScenarioReport::failedStepExecutions).isEqualTo(0)
                prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                prop(ScenarioReport::meters).isNotEmpty()
            }
        }
    }

    protected fun extractScenariosReports(processOutput: List<String>): List<ScenarioReport> {
        // Scan for lines matching:   1/1  scenario-name  STATUS
        val scenarioHeader = Regex("""^\s+\d+/\d+\s{2}(.+?)\s{2}(\S+)\s*$""")
        // Matches:   Start......: 2026-06-26 23:17:39   End: 2026-06-26 23:17:40   Duration: 1s
        val startEndRex =
            Regex("""Start\.*:\s+(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+End:\s+(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+Duration:\s+(.+)""")
        // Matches:   Minions....: 500 scheduled   500 started   500 completed
        val minionRex = Regex("""(\d+) started\s+(\d+) completed""")
        // Matches:   Executions.: 1500 ok   0 failed   ...
        val execRex = Regex("""(\d+) ok\s+(\d+) failed""")
        val dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        val terminalStatuses = setOf(
            ExecutionStatus.SUCCESSFUL, ExecutionStatus.FAILED,
            ExecutionStatus.ABORTED, ExecutionStatus.WARNING
        )

        fun parseDuration(s: String): Duration {
            val h = Regex("""(\d+)h""").find(s)?.groupValues?.get(1)?.toLong() ?: 0L
            val m = Regex("""(\d+)m""").find(s)?.groupValues?.get(1)?.toLong() ?: 0L
            val sec = Regex("""(\d+)s""").find(s)?.groupValues?.get(1)?.toLong() ?: 0L
            return Duration.ofSeconds(h * 3600 + m * 60 + sec)
        }

        fun parseBlock(lines: List<String>): List<ScenarioReport> {
            val result = mutableListOf<ScenarioReport>()
            var scenarioName: String? = null
            var start: Instant? = null
            var end: Instant? = null
            var duration: Duration? = null
            var startedMinions = 0
            var completedMinions = 0
            var successful = 0
            var failed = 0
            var status: ExecutionStatus? = null
            var meters = mutableListOf<String>()

            fun flush() {
                scenarioName?.let { name ->
                    result += ScenarioReport(
                        scenarioName = name,
                        start = start ?: Instant.MIN,
                        end = end,
                        duration = duration,
                        startedMinions = startedMinions,
                        completedMinions = completedMinions,
                        successfulStepExecutions = successful,
                        failedStepExecutions = failed,
                        abort = null,
                        status = status,
                        messages = emptyMap(),
                        meters = meters.toList()
                    )
                }
            }

            for (line in lines) {
                val headerMatch = scenarioHeader.matchEntire(line)
                if (headerMatch != null) {
                    flush()
                    scenarioName = headerMatch.groupValues[1].trim()
                    status = runCatching { ExecutionStatus.valueOf(headerMatch.groupValues[2].trim()) }.getOrNull()
                    start = null; end = null; duration = null
                    startedMinions = 0; completedMinions = 0
                    successful = 0; failed = 0
                    meters = mutableListOf()
                    continue
                }
                if (scenarioName == null) continue
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("Start......:") -> startEndRex.find(trimmed)?.destructured?.let { (s, e, d) ->
                        start =
                            runCatching { LocalDateTime.parse(s, dtFormatter).toInstant(ZoneOffset.UTC) }.getOrNull()
                        end = runCatching { LocalDateTime.parse(e, dtFormatter).toInstant(ZoneOffset.UTC) }.getOrNull()
                        duration = parseDuration(d.trim())
                    }

                    trimmed.startsWith("Minions....:") -> minionRex.find(trimmed)?.destructured?.let { (s, c) ->
                        startedMinions = s.toInt()
                        completedMinions = c.toInt()
                    }

                    trimmed.startsWith("Executions.:") -> execRex.find(trimmed)?.destructured?.let { (ok, ko) ->
                        successful = ok.toInt()
                        failed = ko.toInt()
                    }

                    trimmed.startsWith("- ") -> meters += trimmed
                }
            }
            flush()
            return result
        }

        // Split output into per-block slices at each "CAMPAIGN REPORT" header, parse each, then
        // keep only terminal-status scenarios so in-progress intermediate blocks are ignored.
        val blockStarts = processOutput.indices.filter { processOutput[it].contains("CAMPAIGN REPORT") }
        if (blockStarts.isEmpty()) return emptyList()
        val allScenarios = blockStarts.flatMapIndexed { i, start ->
            val end = if (i + 1 < blockStarts.size) blockStarts[i + 1] else processOutput.size
            parseBlock(processOutput.subList(start, end))
        }
        return allScenarios.filter { it.status in terminalStatuses }.ifEmpty { allScenarios }
    }

    data class ScenarioReport(
        val scenarioName: ScenarioName,
        val start: Instant,
        val startedMinions: Int,
        val completedMinions: Int,
        val successfulStepExecutions: Int,
        val failedStepExecutions: Int,
        val end: Instant?,
        val abort: Instant?,
        val duration: Duration?,
        val status: ExecutionStatus?,
        val messages: Map<Any, ReportMessage>,
        val meters: List<String> = emptyList()
    )
}