package io.qalipsis.core.head.report

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Implementation of [ProcessExitCodeSupplier] to force a failing exit code when the campaign failed.
 *
 * The campaign execution errors are in the range 200.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [AutostartCampaignConfiguration::class])
internal class AutostartCampaignProcessExitSupplier(
    private val autostartCampaignConfiguration: AutostartCampaignConfiguration,
    private val campaignReportStateKeeper: CampaignReportStateKeeper
) : ProcessExitCodeSupplier {

    override suspend fun await(): Optional<Int> {
        val reportStatus = campaignReportStateKeeper.generateReport(autostartCampaignConfiguration.name)?.status
        return if (reportStatus == null || reportStatus.exitCode < 0 || reportStatus == ExecutionStatus.SUCCESSFUL || reportStatus == ExecutionStatus.WARNING) {
            Optional.empty()
        } else {
            Optional.of(200 + reportStatus.exitCode)
        }
    }
}