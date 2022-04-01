package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive

internal open class DisabledState(
    protected val campaign: CampaignConfiguration,
    private val isSuccessful: Boolean = true
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.name) {

    override val isCompleted: Boolean = true

    override suspend fun doInit(): List<Directive> {
        context.headChannel.unsubscribeFeedback(campaign.feedbackChannel)

        if (context.reportPublishers.isNotEmpty()) {
            val report = context.campaignReportStateKeeper.report(campaignName)
            context.reportPublishers.forEach { publisher ->
                tryAndLogOrNull(log) {
                    publisher.publish(campaign, report)
                }
            }
        }

        val directive = CompleteCampaignDirective(
            campaignName = campaignName,
            isSuccessful = isSuccessful,
            message = campaign.message,
            channel = campaign.broadcastChannel
        )
        context.campaignAutoStarter?.completeCampaign(directive)
        return listOf(directive)
    }

    override fun toString(): String {
        return "DisabledState(campaign=$campaign, isSuccessful=$isSuccessful, isCompleted=$isCompleted)"
    }

    private companion object {
        val log = logger()
    }
}