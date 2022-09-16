package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.AbortingState
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState

@ExperimentalLettuceCoroutinesApi
internal class RedisAbortingState(
    campaign: RunningCampaign,
    abortConfiguration: AbortRunningCampaign,
    error: String,
    private val operations: CampaignRedisOperations
) : AbortingState(campaign, abortConfiguration, error) {

    /**
     * This constructor can only be used to rebuild the state, after it was already initialized.
     */
    constructor(
        campaign: RunningCampaign,
        operations: CampaignRedisOperations
    ) : this(
        campaign,
        AbortRunningCampaign(),
        campaign.message!!,
        operations
    )

    override suspend fun doInit(): List<Directive> {
        campaign.message = "Aborting campaign"
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.ABORTING_STATE)
        operations.saveConfiguration(campaign)
        // Prepared the feedback expectations.
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignAbortFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaign.tenant, campaignKey, feedback.nodeId)) {
                if (abortConfiguration.hard) {
                    context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.ABORTED)
                    RedisFailureState(campaign, "The campaign was aborted", operations)
                } else {
                    RedisCompletionState(campaign, operations)
                }
            } else {
                this
            }
        } else {
            this
        }
    }
}