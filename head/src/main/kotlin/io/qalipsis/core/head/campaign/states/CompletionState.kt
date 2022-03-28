package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class CompletionState(
    protected val campaign: CampaignConfiguration
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.id) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
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
                DisabledState(campaign)
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "CompletionState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

}