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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments.HEAD
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.StepExecutionDetails
import jakarta.inject.Singleton
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * [CampaignReportPublisher] that renders the campaign report as a JUnit-compatible XML file
 * (`<testsuites>` / `<testsuite>` / `<testcase>`) suitable for ingestion by CI tools.
 *
 * Mapping:
 * - Campaign → `<testsuites>`
 * - Scenario → `<testsuite>`
 * - Step     → `<testcase>` (one per step, executed or not)
 *   - Not executed → `<skipped/>`
 *   - Messages with severity `ERROR` → `<failure>`
 *   - Messages with severity `ABORT` → `<error>`
 *   - `<system-out>` carries the step ASCII details (status, executions, messages, meters)
 *     rendered by [AsciiReportService].
 *
 * Activated in standalone or head mode when `report.export.junit.enabled=true`. Output file
 * defaults to `./results/<campaign-key>.xml`; the folder can be overridden via
 * `report.export.junit.folder`.
 *
 * @author rklymenko
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(bean = CampaignReportProvider::class),
    Requires(env = [HEAD, STANDALONE]),
    Requires(property = "report.export.junit.enabled", value = "true", defaultValue = "false")
)
class JunitReportPublisher(
    private val campaignReportProvider: CampaignReportProvider,
    private val asciiReportService: AsciiReportService,
    @Property(name = "report.export.junit.folder", defaultValue = "./results") private val outputDir: String
) : CampaignReportPublisher {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport) {
        val details = campaignReportProvider.retrieve(tenant, campaignKey)
        val outputDirectory = File(outputDir).also { it.mkdirs() }
        val outputFile = File(outputDirectory, "$campaignKey.xml")
        outputFile.writeText(buildTestSuites(campaignKey, details), Charsets.UTF_8)
        log.info { "JUnit campaign report written to ${outputFile.absolutePath}" }
    }

    private fun buildTestSuites(campaignKey: CampaignKey, details: CampaignExecutionDetails): String {
        val duration = durationSeconds(details.start, details.end)
        val totalTests = details.scenarios.sumOf { it.steps.size }
        val totalFailures = details.scenarios.sumOf(::countFailedSteps)
        val totalErrors = details.scenarios.sumOf(::countAbortedSteps)
        val totalSkipped = details.scenarios.sumOf { it.steps.count(StepExecutionDetails::notExecuted) }
        val campaignTimestamp = details.start ?: Instant.now()

        return buildString {
            appendLine(XML_HEADER)
            append("<testsuites")
            append(""" id="${xmlAttr(campaignKey)}"""")
            append(""" name="${xmlAttr(details.name)}"""")
            append(""" tests="$totalTests"""")
            append(""" failures="$totalFailures"""")
            append(""" errors="$totalErrors"""")
            append(""" skipped="$totalSkipped"""")
            append(""" time="$duration"""")
            appendLine(">")
            details.scenarios.forEach { appendScenario(this, campaignKey, it, campaignTimestamp) }
            append("</testsuites>")
        }
    }

    private fun appendScenario(
        sb: StringBuilder,
        campaignKey: CampaignKey,
        scenario: ScenarioExecutionDetails,
        campaignTimestamp: Instant
    ) {
        val duration = durationSeconds(scenario.start, scenario.end)
        val tests = scenario.steps.size
        val failures = countFailedSteps(scenario)
        val errors = countAbortedSteps(scenario)
        val skipped = scenario.steps.count(StepExecutionDetails::notExecuted)
        val timestamp = scenario.start ?: campaignTimestamp

        sb.append(TAB).append("<testsuite")
        sb.append(""" id="${xmlAttr(campaignKey)}-${xmlAttr(scenario.name)}"""")
        sb.append(""" name="${xmlAttr(scenario.name)}"""")
        sb.append(""" tests="$tests"""")
        sb.append(""" failures="$failures"""")
        sb.append(""" errors="$errors"""")
        sb.append(""" skipped="$skipped"""")
        sb.append(""" timestamp="$timestamp"""")
        sb.append(""" hostname="Qalipsis"""")
        sb.append(""" time="$duration"""")
        sb.appendLine(">")
        scenario.steps.forEach { appendTestCase(sb, scenario, it) }
        sb.append(TAB).appendLine("</testsuite>")
    }

    private fun appendTestCase(sb: StringBuilder, scenario: ScenarioExecutionDetails, step: StepExecutionDetails) {
        sb.append(TAB.repeat(2)).append("<testcase")
        sb.append(""" name="${xmlAttr(step.name)}"""")
        sb.append(""" classname="${xmlAttr(scenario.name)}"""")
        sb.append(""" time="0"""")
        sb.appendLine(">")

        if (step.notExecuted) {
            sb.append(TAB.repeat(3)).appendLine("""<skipped message="Step not executed"/>""")
        } else {
            step.messages.filter { it.severity == ReportMessageSeverity.ERROR }
                .forEach { appendReportedFailure(sb, it, "failure") }
            step.messages.filter { it.severity == ReportMessageSeverity.ABORT }
                .forEach { appendReportedFailure(sb, it, "error") }
        }

        val systemOut = asciiReportService.renderStepDetails(step)
        if (systemOut.isNotBlank()) {
            sb.append(TAB.repeat(3)).appendLine("<system-out>")
            sb.appendLine(cdata(systemOut))
            sb.append(TAB.repeat(3)).appendLine("</system-out>")
        }
        sb.append(TAB.repeat(2)).appendLine("</testcase>")
    }

    private fun appendReportedFailure(sb: StringBuilder, msg: ReportMessage, element: String) {
        sb.append(TAB.repeat(3))
        sb.append("<$element")
        sb.append(""" message="${xmlAttr(msg.message)}"""")
        sb.append(""" type="${msg.severity}"""")
        sb.append(">")
        sb.append(cdata(msg.message))
        sb.appendLine("</$element>")
    }

    private fun durationSeconds(start: Instant?, end: Instant?): Long =
        if (start != null && end != null) Duration.between(start, end).toSeconds() else 0L

    private fun countFailedSteps(scenario: ScenarioExecutionDetails): Int =
        scenario.steps.count { step -> step.messages.any { it.severity == ReportMessageSeverity.ERROR } }

    private fun countAbortedSteps(scenario: ScenarioExecutionDetails): Int =
        scenario.steps.count { step -> step.messages.any { it.severity == ReportMessageSeverity.ABORT } }

    private fun xmlAttr(value: String?): String {
        if (value == null) return ""
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // Protects the CDATA payload against an embedded terminator by splitting it across two sections.
    private fun cdata(value: String): String = "<![CDATA[${value.replace("]]>", "]]]]><![CDATA[>")}]]>"

    private companion object {
        val log = logger()
        const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8"?>"""
        const val TAB = "  "
    }
}
