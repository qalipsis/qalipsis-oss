package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ReportPublisher
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import jakarta.inject.Named
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.annotation.PreDestroy
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of a [ReportPublisher] storing report in JUnit-like files.
 *
 * @author rklymenko
 */
@Context
@Requirements(
    Requires(env = [ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.AUTOSTART]),
    Requires(beans = [CampaignConfiguration::class]),
    Requires(property = "report.export.junit.enabled", notEquals = "false")
)
internal class StandaloneJunitReportPublisher(
    private val campaign: CampaignConfiguration,
    private val campaignStateKeeper: CampaignReportStateKeeper,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val backgroundContext: CoroutineContext,
    @Value("\${report.export.junit.folder}") private val reportFolder: String
) : ReportPublisher {

    /**
     * Retrieves a report by campaignId and stores it in junit-like format inside files.
     */
    override suspend fun publish(campaignId: CampaignId) {
        val report = campaignStateKeeper.report(campaignId)
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }!!

        report.scenariosReports.forEach {
            writeScenarioToFile(it, duration)
        }
    }

    private fun writeScenarioToFile(scenarioReport: ScenarioReport, duration: Long) {
        File(reportFolder + "/${scenarioReport.scenarioId}.xml").writeText(
            REPORT_HEADER + scenarioReportToText(
                scenarioReport,
                duration
            )
        )
    }

    private fun scenarioReportToText(scenarioReport: ScenarioReport, duration: Long): String {
        val result = StringBuilder()
        result.append(generateTestSuiteHeader(scenarioReport, duration))
        result.append(generateTestSuites(scenarioReport))
        result.append(TEST_SUITE_FOOTER)

        return result.toString()
    }

    private fun generateTestSuiteHeader(scenarioReport: ScenarioReport, duration: Long): String {
        val tests = scenarioReport.messages.size
        val failures = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ABORT }.size
        val errors = scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ERROR }.size
        val timestamp = Instant.now()
        return """<testsuite name="${scenarioReport.scenarioId}" tests="$tests" skipped="0" failures="$failures" errors="$errors" timestamp="$timestamp" hostname="Qalipsis" time="$duration">"""

    }

    private fun generateTestSuites(scenarioReport: ScenarioReport): String {
        val time = scenarioReport.end?.let { Duration.between(scenarioReport.start, it).toSeconds() }
        return scenarioReport.messages.map { message ->
            if (isFailedTestCase(message)) """
    <testcase name="${message.stepId}" time="$time">
        <failure message="${message.message}" type = "${message.severity}" />
    </testcase>"""
            else """    
    <testcase name="${message.stepId}" time="$time" />"""
        }.joinToString("")
    }

    private fun isFailedTestCase(reportMessage: ReportMessage) =
        reportMessage.severity == ReportMessageSeverity.ERROR || reportMessage.severity == ReportMessageSeverity.ABORT


    @PreDestroy
    fun publishOnLeave() {
        tryAndLogOrNull(log) {
            runBlocking(backgroundContext) {
                publish(campaign.id)
            }
        }
    }

    companion object {
        private val log = logger()

        private const val REPORT_HEADER = """<?xml version="1.0" encoding="UTF-8"?>        
"""
        private const val TEST_SUITE_FOOTER = """   
    <system-out><![CDATA[
    Concatenated list of messages (separator is \n\n), grouped by steps.
    ]]></system-out>
    <system-err><![CDATA[]]></system-err>
</testsuite>
"""
    }
}