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
import io.qalipsis.api.context.ScenarioId
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
internal abstract class AbstractDeploymentIntegrationTest {

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
                prop(ScenarioReport::scenarioId).isEqualTo("deployment-test")
                prop(ScenarioReport::start).isBetween(before, after)
                prop(ScenarioReport::end).isNotNull().isBetween(before, after)
                prop(ScenarioReport::duration).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.between(before, after))
                }
                prop(ScenarioReport::startedMinions).isEqualTo(10_000)
                // FIXME prop(ScenarioReport::completedMinions).isEqualTo(10_000)
                prop(ScenarioReport::successfulStepExecutions).isEqualTo(20_000)
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
                scenarioId = scenario,
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
        val scenarioId: ScenarioId,
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