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
            }
        }
    }

    protected fun extractScenariosReports(processOutput: List<String>): List<ScenarioReport> {
        val report = processOutput.dropWhile { !it.contains("SCENARIO REPORT") }.toList()
        val executedScenarios = report.filter { it.startsWith("Scenario..") }.map { it.substringAfterLast("....") }
        val starts =
            report.filter { it.startsWith("Start..") }.map { it.substringAfterLast("....") }.map { Instant.parse(it) }
        val ends =
            report.filter { it.startsWith("End..") }.map { it.substringAfterLast("....") }.map { Instant.parse(it) }
        val startedMinions =
            report.filter { it.startsWith("Started minions..") }.map { it.substringAfterLast("....").toInt() }
        val completedMinions =
            report.filter { it.startsWith("Completed minions..") }.map { it.substringAfterLast("....").toInt() }
        val successfulExecutions =
            report.filter { it.startsWith("Successful steps executions..") }
                .map { it.substringAfterLast("....").toInt() }
        val failedExecutions =
            report.filter { it.startsWith("Failed steps executions..") }.map { it.substringAfterLast("....").toInt() }
        val status = report.filter { it.startsWith("Status..") }.map { it.substringAfterLast("....") }
            .map { ExecutionStatus.valueOf(it) }

        return executedScenarios.mapIndexed { index, scenario ->
            ScenarioReport(
                scenarioName = scenario,
                start = starts[index],
                end = ends[index],
                duration = Duration.between(starts[index], ends[index]),
                startedMinions = startedMinions[index],
                completedMinions = completedMinions[index],
                successfulStepExecutions = successfulExecutions[index],
                failedStepExecutions = failedExecutions[index],
                abort = null,
                status = status[index],
                messages = emptyMap()
            )
        }
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
        val messages: Map<Any, ReportMessage>
    )
}