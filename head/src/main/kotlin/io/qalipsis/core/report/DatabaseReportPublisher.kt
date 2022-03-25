package io.qalipsis.core.report

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.ReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments.VOLATILE
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.campaign.CampaignReportService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper

/**
 * Implementation of a [ReportPublisher] persisting the report into the db. It is mainly used when executing QALIPSIS
 * as standalone.
 *
 * @author Palina Bril
 */
@Context
@Requirements(
    Requires(notEnv = [VOLATILE]),
    Requires(beans = [AutostartCampaignConfiguration::class]),
    Requires(property = "report.export.database.enabled", notEquals = "false")
)
internal class DatabaseReportPublisher(
    private val campaign: AutostartCampaignConfiguration,
    private val campaignReportStateKeeper: CampaignReportStateKeeper,
    private val campaignReportService: CampaignReportService
) : ReportPublisher {

    override suspend fun publish(campaignId: CampaignId) {
        val report = campaignReportStateKeeper.report(campaignId)
        campaignReportService.save(report)
    }
}
