package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Implementation of a [ReportPublisher] storing report in JUnit-like files.
 *
 * @author rklymenko
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(property = "report.export.junit.enabled", defaultValue = "false", value = "true")
)
internal class JunitReportPublisher(
    @Value("\${report.export.junit.folder}") private val reportFolder: String
) : CampaignReportPublisher {

    override suspend fun publish(campaign: CampaignConfiguration, report: CampaignReport) {
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }!!
        val dir = File(reportFolder, campaign.key)
        dir.mkdirs()

        report.scenariosReports.forEach {
            writeScenarioToFile(dir, it, duration)
        }
    }

    private fun writeScenarioToFile(directory: File, scenarioReport: ScenarioReport, duration: Long) {
        File(directory, "${scenarioReport.scenarioName}.xml").writeText(
            REPORT_HEADER + scenarioReportToText(
                scenarioReport,
                duration
            )
        )
    }

    private fun scenarioReportToText(scenarioReport: ScenarioReport, duration: Long): String {
        val out = scenarioReport.messages.filterNot(::isFailedTestCase)
            .joinToString("\n", transform = ::buildConsoleMessage)
        val err = scenarioReport.messages.filter(::isFailedTestCase)
            .joinToString("\n", transform = ::buildConsoleMessage)

        return """${generateTestSuiteHeader(scenarioReport, duration)}
  ${generateTestSuites(scenarioReport)}
  <system-out><![CDATA[
$out
  ]]></system-out>
  <system-err><![CDATA[
$err
  ]]></system-err>
</testsuite>
"""
    }

    private fun buildConsoleMessage(it: ReportMessage): String {
        val severity = "${it.severity}".padEnd(5)
        return "$severity Step ${it.stepName}: ${it.message}"
    }

    private fun generateTestSuiteHeader(scenarioReport: ScenarioReport, duration: Long): String {
        val tests = scenarioReport.messages.size
        val failures = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ABORT }.size
        val errors = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ERROR }.size
        val timestamp = Instant.now()
        return """<testsuite name="${scenarioReport.scenarioName}" tests="$tests" skipped="0" failures="$failures" errors="$errors" timestamp="$timestamp" hostname="Qalipsis" time="$duration">"""
    }

    private fun generateTestSuites(scenarioReport: ScenarioReport): String {
        val time = scenarioReport.end.let { Duration.between(scenarioReport.start, it).toSeconds() }
        return scenarioReport.messages.joinToString("\n  ") { message ->
            if (isFailedTestCase(message)) """<testcase name="${message.stepName}" time="$time">
    <failure message="${message.message}" type="${message.severity}"/>
  </testcase>"""
            else """<testcase name="${message.stepName}" time="$time"/>"""
        }
    }

    private fun isFailedTestCase(reportMessage: ReportMessage) =
        reportMessage.severity == ReportMessageSeverity.ERROR || reportMessage.severity == ReportMessageSeverity.ABORT

    companion object {

        private const val REPORT_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
"""
    }
}