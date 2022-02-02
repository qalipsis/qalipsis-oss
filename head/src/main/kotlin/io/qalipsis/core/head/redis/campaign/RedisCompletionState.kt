package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.CompletionState

@ExperimentalLettuceCoroutinesApi
internal class RedisCompletionState(
    campaign: CampaignConfiguration,
    private val operations: CampaignRedisOperations
) : CompletionState(campaign) {

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaignId, CampaignRedisState.COMPLETION_STATE)
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaignId, feedback.nodeId)) {
                RedisDisabledState(campaign, true, operations)
            } else {
                this
            }
        } else {
            this
        }
    }
}