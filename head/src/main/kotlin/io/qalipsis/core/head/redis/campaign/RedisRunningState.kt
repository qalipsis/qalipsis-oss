package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.RunningState

@ExperimentalLettuceCoroutinesApi
internal class RedisRunningState(
    campaign: CampaignConfiguration,
    private val operations: CampaignRedisOperations,
    private val doNotPersistStateOnInit: Boolean = false,
    directivesForInit: List<Directive> = emptyList()
) : RunningState(campaign, directivesForInit) {

    override suspend fun doInit(): List<Directive> {
        if (!doNotPersistStateOnInit) {
            operations.setState(campaignId, CampaignRedisState.RUNNING_STATE)
        }
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return when {
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)
            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)
            feedback is FailedCampaignFeedback -> RedisFailureState(
                campaign,
                feedback.error,
                operations
            )
            feedback is CompleteMinionFeedback -> RedisRunningState(
                campaign, operations, true, listOf(
                    MinionsShutdownDirective(
                        campaign.id,
                        feedback.scenarioId,
                        listOf(feedback.minionId),
                        idGenerator.short(),
                        campaign.broadcastChannel
                    )
                )
            )
            feedback is EndOfCampaignScenarioFeedback -> {
                campaignReportStateKeeper.complete(feedback.campaignId, feedback.scenarioId)
                RedisRunningState(
                    campaign, operations, true, listOf(
                        CampaignScenarioShutdownDirective(
                            campaign.id,
                            feedback.scenarioId,
                            idGenerator.short(),
                            campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is EndOfCampaignFeedback -> {
                campaignReportStateKeeper.complete(feedback.campaignId)
                RedisCompletionState(campaign, operations)
            }
            else -> this
        }
    }
}