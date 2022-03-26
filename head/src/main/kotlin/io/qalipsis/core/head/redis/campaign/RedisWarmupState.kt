package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.WarmupState

@ExperimentalLettuceCoroutinesApi
internal class RedisWarmupState(
    campaign: CampaignConfiguration,
    private val operations: CampaignRedisOperations
) : WarmupState(campaign) {

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaignId, CampaignRedisState.WARMUP_STATE)
        operations.prepareAssignmentsForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is ScenarioWarmUpFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                RedisFailureState(campaign, feedback.error ?: "", operations)
            } else {
                if (feedback.status == FeedbackStatus.IGNORED) {
                    operations.saveConfiguration(campaign)
                }
                if (operations.markFeedbackForFactoryScenario(campaignId, feedback.nodeId, feedback.scenarioId)) {
                    RedisMinionsStartupState(campaign, operations)
                } else {
                    this
                }
            }
        } else {
            this
        }
    }
}