package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class FailureState(
    protected val campaign: CampaignConfiguration,
    private val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.id) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        campaign.message = error
        return listOf(
            CampaignShutdownDirective(
                campaignId = campaignId,
                channel = campaign.broadcastChannel
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
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