package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportPublisher
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
    value = [
        Requires(env = ["standalone", "autostart"]),
        Requires(property = "report.export.console.enabled", notEquals = "false")
    ]
)
internal class StandaloneConsoleReportPublisher(
    @Property(name = "campaign.name") private val campaignName: String,
    private val campaignStateKeeper: CampaignStateKeeper
) : ReportPublisher {

    override fun publish(campaignId: CampaignId) {
        val report = campaignStateKeeper.report(campaignId)
        val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }

        println(
            """
============================================================
=====================  CAMPAIGN REPORT =====================
============================================================   

Campaign...........................${report.campaignId}
Start..............................${report.start}
End................................${report.end ?: "<Running>"}
Duration...........................${duration?.let { "$it seconds" } ?: "<Running>"} 
Configured minions..................${report.configuredMinionsCount}
Completed minions..................${report.executedMinionsCount}
Successful steps executions........${report.successfulExecutions}
Failed steps executions............${report.failedExecutions}
Status.............................${report.status}
   
        """
        )

        report.scenariosReports.forEach { scenarioReport ->
            val duration = scenarioReport.end?.let { Duration.between(report.start, it).toSeconds() }
            println(
                """
=====================  SCENARIO REPORT =====================
Scenario...........................${scenarioReport.scenarioId}
Start..............................${scenarioReport.start}
End................................${scenarioReport.end ?: "<Running>"}
Duration...........................${duration?.let { "$it seconds" } ?: "<Running>"} 
Configured minions..................${scenarioReport.configuredMinionsCount}
Completed minions..................${scenarioReport.executedMinionsCount}
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
        publish(campaignName)
    }

}
