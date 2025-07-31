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

package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.context.CampaignKey
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
 * Implementation of a [CampaignReportPublisher] storing report in JUnit-like files.
 *
 * @author rklymenko
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(property = "report.export.junit.enabled", defaultValue = "false", value = "true")
)
class JunitReportPublisher(
    @Value("\${report.export.junit.folder}") private val reportFolder: String
) : CampaignReportPublisher {

    override suspend fun publish(campaignKey: CampaignKey, report: CampaignReport) {
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }!!
        val dir = File(reportFolder, campaignKey)
        dir.mkdirs()
        writeReportToFile(dir, campaignKey, report, duration)
    }

    private fun writeReportToFile(directory: File, campaignKey: CampaignKey, report: CampaignReport, duration: Long) {
        val failures =
            report.scenariosReports.fold(0) { acc, scenarioReport -> acc + scenarioReport.messages.filter(::isFailedTestCase).size }
        val wrapperTag =
            """<testsuites id="${report.campaignKey}" name="${report.campaignKey}" tests="${(report.successfulExecutions ?: 0) + (report.failedExecutions ?: 0)}" failures="$failures" time="${duration}s">"""
        File(directory, "${report.scenariosReports[0].scenarioName}.xml").writeText(
            """
                |${REPORT_HEADER.trimMargin()}
                |$wrapperTag
                |${
                report.scenariosReports.joinToString("\n") {
                    scenarioReportToText(it, duration, campaignKey)
                }.trimMargin()
            }
                |</testsuites>
            """.trimMargin()
        )
    }

    private fun scenarioReportToText(scenarioReport: ScenarioReport, duration: Long, campaignKey: CampaignKey): String {
        val out = scenarioReport.messages.filterNot(::isFailedTestCase)
            .joinToString("\n", transform = ::buildConsoleMessage)
        val err = scenarioReport.messages.filter(::isFailedTestCase)
            .joinToString("\n", transform = ::buildConsoleMessage)

        return """${generateTestSuiteHeader(scenarioReport, duration, campaignKey)}
  ${generateTestSuites(scenarioReport, campaignKey)}
  <system-out><![CDATA[
$out
  ]]></system-out>
  <system-err><![CDATA[

  ]]></system-err>
|    </testsuite>
""".trimMargin()
    }

    private fun buildConsoleMessage(it: ReportMessage): String {
        val severity = "${it.severity}".padEnd(5)
        return "$severity Step ${it.stepName}: ${it.message}"
    }

    private fun generateTestSuiteHeader(
        scenarioReport: ScenarioReport,
        duration: Long,
        campaignKey: CampaignKey
    ): String {
        val tests = (scenarioReport.successfulExecutions?.plus(scenarioReport.failedExecutions!!)) ?: 0
        val errors = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ABORT }.size
        val failures = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ERROR }.size
        val timestamp = Instant.now()
        return TAB + """<testsuite id="${campaignKey + "-" + scenarioReport.scenarioName}" name="${scenarioReport.scenarioName}" tests="$tests" skipped="0" failures="$failures" errors="$errors" timestamp="$timestamp" hostname="Qalipsis" time="${duration}s">"""
    }

    private fun generateTestSuites(scenarioReport: ScenarioReport, campaignKey: CampaignKey): String {
        val keyStore = mutableMapOf<String, MutableList<ReportMessage>>()
        scenarioReport.messages.forEach { reportMessage ->
            val messages = keyStore.getOrPut(reportMessage.stepName) { mutableListOf() }
            messages.add(reportMessage)
        }
        return buildString {
            keyStore.forEach { (stepName, reportMessages) ->
                val failures = countFailedTestCase(reportMessages)
                val testCaseId = "${campaignKey}-${scenarioReport.scenarioName}-$stepName"
                val failureTags = reportMessages.filter { isFailedTestCase(it) }
                    .joinToString(" \n") {
                        TAB.repeat(3) + """<failure message="${it.message}" type="${it.severity}"/>""".trimMargin()
                    }
                if (failureTags.isNotEmpty()) {
                    appendLine(TAB.repeat(2) + """<testcase id="$testCaseId" name="$stepName" time="0" tests="${reportMessages.size}" failures="$failures">""")
                    appendLine(failureTags)
                    appendLine(TAB.repeat(2) + """</testcase>""".trimMargin())
                } else {
                    appendLine(TAB.repeat(2) + """<testcase id="$testCaseId" name="$stepName" time="0" tests="${reportMessages.size}" failures="$failures"/>""".trimMargin())
                }

            }
        }.trimMargin()
    }

    private fun isFailedTestCase(reportMessage: ReportMessage) =
        reportMessage.severity == ReportMessageSeverity.ERROR || reportMessage.severity == ReportMessageSeverity.ABORT

    private fun countFailedTestCase(reportMessages: List<ReportMessage>) =
        reportMessages.count { isFailedTestCase(it) }

    companion object {

        private const val REPORT_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
"""
        private const val TAB = "  "
    }
}