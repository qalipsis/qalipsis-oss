package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FailureState

@ExperimentalLettuceCoroutinesApi
internal class RedisFailureState(
    campaign: RunningCampaign,
    error: String,
    private val operations: CampaignRedisOperations
) : FailureState(campaign, error) {

    /**
     * This constructor can only be used to rebuild the state, after it was already initialized.
     */
    constructor(
        campaign: RunningCampaign,
        operations: CampaignRedisOperations
    ) : this(
        campaign,
        campaign.message,
        operations
    )

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.FAILURE_STATE)
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaign.tenant, campaignKey, feedback.nodeId)) {
                context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.FAILED)
                RedisDisabledState(campaign, false, operations)
            } else {
                this
            }
        } else {
            this
        }
    }
}