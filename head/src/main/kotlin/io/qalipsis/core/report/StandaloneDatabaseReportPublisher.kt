package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.ReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments.AUTOSTART
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.campaign.PersistentCampaignReportService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper

/**
 * Implementation of a [ReportPublisher] displaying the report in the console. It is mainly used when executing QALIPSIS
 * as standalone.
 *
 * @author Palina Bril
 */
@Context
@Requirements(
    Requires(env = [STANDALONE]),
    Requires(env = [AUTOSTART]),
    Requires(beans = [AutostartCampaignConfiguration::class]),
    Requires(property = "report.export.database.enabled", notEquals = "false")
)
internal class StandaloneDatabaseReportPublisher(
    private val campaign: AutostartCampaignConfiguration,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val campaignReportService: PersistentCampaignReportService
) : ReportPublisher {

    override suspend fun publish(campaignId: CampaignId) {
        val report = campaignReportStateKeeper.report(campaignId)
        campaignReportService.save(report)
    }
}
