package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.MinionsAssignmentState

@ExperimentalLettuceCoroutinesApi
internal class RedisMinionsAssignmentState(
    campaign: RunningCampaign,
    private val operations: CampaignRedisOperations
) : MinionsAssignmentState(campaign) {

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.MINIONS_ASSIGNMENT_STATE)
        operations.prepareAssignmentsForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            RedisFailureState(campaign, feedback.error ?: "", operations)
        } else if (feedback is MinionsAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                RedisFailureState(campaign, feedback.error ?: "", operations)
            } else {
                if (feedback.status == FeedbackStatus.IGNORED) {
                    operations.saveConfiguration(campaign)
                }
                if (operations.markFeedbackForFactoryScenario(
                        campaign.tenant,
                        campaignKey,
                        feedback.nodeId,
                        feedback.scenarioName
                    )
                ) {
                    RedisWarmupState(campaign, operations)
                } else {
                    this
                }
            }
        } else {
            this
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return RedisAbortingState(campaign, abortConfiguration, "The campaign was aborted", operations)
    }
}