package io.qalipsis.core.head.campaign.states

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class AbortingState(
    protected val campaign: RunningCampaign,
    protected val abortConfiguration: AbortRunningCampaign,
    protected val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        campaign.message = "Aborting campaign"
        return listOf(
            CampaignAbortDirective(
                campaignKey = campaignKey,
                channel = campaign.broadcastChannel,
                scenarioNames = campaign.scenarios.keys.toList(),
                abortRunningCampaign = AbortRunningCampaign(abortConfiguration.hard)
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignAbortFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
                if (abortConfiguration.hard) {
                    context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.ABORTED)
                    FailureState(campaign, "The campaign was aborted")
                } else {
                    CompletionState(campaign)
                }
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "AbortingState(campaign=$campaign, abortConfiguration = $abortConfiguration," +
                " error='$error', expectedFeedbacks=$expectedFeedbacks)"
    }
}