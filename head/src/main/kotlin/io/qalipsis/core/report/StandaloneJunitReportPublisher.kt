package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ReportPublisher
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignConfiguration
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.annotation.PreDestroy

/**
 * Implementation of a [ReportPublisher] storing report in junit-like format inside files. It is mainly used when executing QALIPSIS
 * as standalone.
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
class StandaloneJunitReportPublisher(
    private val campaign: CampaignConfiguration,
    private val campaignStateKeeper: CampaignStateKeeper,
    @Value("\${report.export.junit.folder}") private val reportFolder: String): ReportPublisher {

    private val REPORT_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

    private val TEST_SUITE_FOOTER = "<system-out><![CDATA[\n" +
                                    "   Concatenated list of messages (separator is \\n\\n), grouped by steps.\n" +
                                    "   ]]></system-out>\n" +
                                    "<system-err><![CDATA[]]></system-err>"

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
        File(reportFolder + "/${scenarioReport.scenarioId}.xml").writeText(REPORT_HEADER + scenarioReportToText(scenarioReport, duration))
    }

    private fun scenarioReportToText(scenarioReport: ScenarioReport, duration: Long) =
        """
<testsuite name="${scenarioReport.scenarioId}" tests="${scenarioReport.messages.size}" skipped="0" failures="${scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ABORT }.size}" errors="${scenarioReport.messages.filter { it.severity == ReportMessageSeverity.ERROR }.size}" timestamp="${Instant.now()}" hostname="localhost" time="$duration">
    ${scenarioReport.messages.map { message -> "<testcase name=\"${message.stepId}\" time=\"${scenarioReport.end?.let { Duration.between(scenarioReport.start, it).toSeconds() }}\"> ${if(message.severity == ReportMessageSeverity.ERROR || message.severity == ReportMessageSeverity.ABORT) "<failure message=\"${message.message}\" type=\"${message.severity}\" />" else ""}</testcase>" }.joinToString("\n \t")}
    ${TEST_SUITE_FOOTER}
</testsuite>
""".trimIndent()

    @PreDestroy
    fun publishOnLeave() {
        tryAndLogOrNull(log) {
            runBlocking {
                publish(campaign.id)
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}