package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.MinionsStartupState

@ExperimentalLettuceCoroutinesApi
internal class RedisMinionsStartupState(
    campaign: CampaignConfiguration,
    private val operations: CampaignRedisOperations
) : MinionsStartupState(campaign) {

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaignId, CampaignRedisState.MINIONS_STARTUP_STATE)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            RedisFailureState(campaign, feedback.error ?: "", operations)
        } else if (feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED) {
            RedisFailureState(campaign, feedback.error ?: "", operations)
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED) {
            RedisFailureState(campaign, feedback.error ?: "", operations)
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.COMPLETED) {
            RedisRunningState(campaign, operations)
        } else {
            this
        }
    }
}