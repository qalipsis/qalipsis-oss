package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FailureState

@ExperimentalLettuceCoroutinesApi
internal class RedisFailureState(
    campaign: CampaignConfiguration,
    error: String,
    private val operations: CampaignRedisOperations
) : FailureState(campaign, error) {

    /**
     * This constructor can only be used to rebuild the state, after it was already initialized.
     */
    constructor(
        campaign: CampaignConfiguration,
        operations: CampaignRedisOperations
    ) : this(
        campaign,
        campaign.message!!,
        operations
    )

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaign.tenant, campaignName, CampaignRedisState.FAILURE_STATE)
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaign.tenant, campaignName, feedback.nodeId)) {
                RedisDisabledState(campaign, false, operations)
            } else {
                this
            }
        } else {
            this
        }
    }
}