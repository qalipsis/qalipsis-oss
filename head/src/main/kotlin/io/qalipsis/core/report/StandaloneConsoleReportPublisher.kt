package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.ReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import kotlinx.coroutines.runBlocking
import java.time.Duration
import javax.annotation.PreDestroy

/**
 * Implementation of a [ReportPublisher] displaying the report in the console. It is mainly used when executing QALIPSIS
 * as standalone.
 *
 * @author Eric Jessé
 */
@Context
@Requirements(
    Requires(env = [STANDALONE]),
    Requires(env = [AUTOSTART]),
    Requires(beans = [AutostartCampaignConfiguration::class]),
    Requires(property = "report.export.console.enabled", notEquals = "false")
)
internal class StandaloneConsoleReportPublisher(
    private val campaign: AutostartCampaignConfiguration,
    private val campaignReportStateKeeper: CampaignReportStateKeeper
) : ReportPublisher {

    override suspend fun publish(campaignId: CampaignId) {
        val report = campaignReportStateKeeper.report(campaignId)
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }

        println(
            """
============================================================
=====================  CAMPAIGN REPORT =====================
============================================================   

Campaign...........................${report.campaignId}
Start..............................${report.start}
End................................${report.end ?: RUNNING_INDICATOR}
Duration...........................${duration?.let { "$it seconds" } ?: RUNNING_INDICATOR} 
Started minions....................${report.startedMinions}
Completed minions..................${report.completedMinions}
Successful steps executions........${report.successfulExecutions}
Failed steps executions............${report.failedExecutions}
Status.............................${report.status}
   
        """
        )

        report.scenariosReports.forEach { scenarioReport ->
            val scenarioDuration = scenarioReport.end?.let { Duration.between(report.start, it).toSeconds() }
            println(
                """
=====================  SCENARIO REPORT =====================
Scenario...........................${scenarioReport.scenarioId}
Start..............................${scenarioReport.start}
End................................${scenarioReport.end ?: RUNNING_INDICATOR}
Duration...........................${scenarioDuration.let { "$it seconds" } ?: RUNNING_INDICATOR} 
Started minions....................${scenarioReport.startedMinions}
Completed minions..................${scenarioReport.completedMinions}
Successful steps executions........${scenarioReport.successfulExecutions}
Failed steps executions............${scenarioReport.failedExecutions}
Status.............................${scenarioReport.status}
Messages:
${
                    if (scenarioReport.messages.isEmpty()) "\tNone" else scenarioReport.messages.joinToString(
                        "\n"
                    ) {
                        "- ${it.severity}:".padEnd(10) + "step '${it.stepId}' - ${it.message}"
                    }
                }
        """
            )
        }

    }

    @PreDestroy
    fun publishOnLeave() {
        runCatching {
            runBlocking {
                publish(campaign.id)
            }
        }
    }

    companion object {

        private const val RUNNING_INDICATOR = "<Running>"

    }

}
