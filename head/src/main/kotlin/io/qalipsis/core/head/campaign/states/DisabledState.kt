package io.qalipsis.core.head.campaign.states

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive

internal open class DisabledState(
    protected val campaign: RunningCampaign,
    private val isSuccessful: Boolean = true
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override val isCompleted: Boolean = true

    override suspend fun doInit(): List<Directive> {
        context.factoryService.releaseFactories(campaign, campaign.factories.keys)
        context.headChannel.unsubscribeFeedback(campaign.feedbackChannel)

        if (context.reportPublishers.isNotEmpty()) {
            context.campaignReportStateKeeper.generateReport(campaignKey)?.let { report ->
                context.reportPublishers.forEach { publisher ->
                    tryAndLogOrNull(log) {
                        publisher.publish(campaign.key, report)
                    }
                }
            }
        }

        val directive = CompleteCampaignDirective(
            campaignKey = campaignKey,
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