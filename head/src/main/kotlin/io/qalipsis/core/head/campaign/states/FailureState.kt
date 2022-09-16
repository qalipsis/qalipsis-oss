package io.qalipsis.core.head.campaign.states

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class FailureState(
    protected val campaign: RunningCampaign,
    private val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        campaign.message = error
        return listOf(
            CampaignShutdownDirective(
                campaignKey = campaignKey,
                channel = campaign.broadcastChannel
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
                context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.FAILED)
                DisabledState(campaign, false)
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "FailureState(campaign=$campaign, error='$error', expectedFeedbacks=$expectedFeedbacks)"
    }

}