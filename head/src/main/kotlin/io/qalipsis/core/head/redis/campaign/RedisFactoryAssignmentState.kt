package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FactoryAssignmentState

@ExperimentalLettuceCoroutinesApi
internal class RedisFactoryAssignmentState(
    campaign: CampaignConfiguration,
    private val operations: CampaignRedisOperations
) : FactoryAssignmentState(campaign) {

    override suspend fun doInit(): List<Directive> {
        // Persists the current state.
        operations.setState(campaign.tenant, campaignName, CampaignRedisState.FACTORY_DAGS_ASSIGNMENT_STATE)
        operations.saveConfiguration(campaign)
        // Prepared the feedback expectations.
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is FactoryAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                RedisFailureState(campaign, feedback.error ?: "", operations)
            } else {
                if (feedback.status == FeedbackStatus.IGNORED) {
                    operations.saveConfiguration(campaign)
                }
                if (operations.markFeedbackForFactory(campaign.tenant, campaignName, feedback.nodeId)) {
                    RedisMinionsAssignmentState(campaign, operations)
                } else {
                    this
                }
            }
        } else {
            this
        }
    }
}