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
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments.HEAD
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter
import jakarta.annotation.Nullable
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of a [ReportPublisher] displaying the report in the console. It is mainly used when executing QALIPSIS
 * as standalone.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [HEAD, STANDALONE]),
    Requires(property = "report.export.console.enabled", defaultValue = "false", value = "true")
)
class ConsoleReportPublisher(
    @Nullable private val consoleCampaignProgressionReporter: ConsoleCampaignProgressionReporter?
) : CampaignReportPublisher {

    override suspend fun publish(campaignKey: CampaignKey, report: CampaignReport) {
        consoleCampaignProgressionReporter?.report() ?: run {
            val duration = report.end?.let { Duration.between(report.start, it).toSeconds() }

            println(
                """
============================================================
=====================  CAMPAIGN REPORT =====================
============================================================   

Campaign...........................${report.campaignKey}
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
                val scenarioDuration = scenarioReport.end.let { Duration.between(report.start, it).toSeconds() }
                println(
                    """
=====================  SCENARIO REPORT =====================
Scenario...........................${scenarioReport.scenarioName}
Start..............................${scenarioReport.start}
End................................${scenarioReport.end}
Duration...........................${scenarioDuration.let { "$it seconds" }} 
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
                            "- ${it.severity}:".padEnd(10) + "step '${it.stepName}' - ${it.message}"
                        }
                    }
        """
                )
            }
        }
    }

    companion object {

        private const val RUNNING_INDICATOR = "<Running>"

    }

}
