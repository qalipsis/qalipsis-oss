package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsShutdownDirective
import io.qalipsis.core.feedbacks.CampaignScenarioShutdownFeedback
import io.qalipsis.core.feedbacks.CompleteMinionFeedback
import io.qalipsis.core.feedbacks.EndOfCampaignScenarioFeedback
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
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
            operations.setState(campaignName, CampaignRedisState.RUNNING_STATE)
            operations.prepareScenariosForFeedbackExpectations(campaign)
        }
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
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
                        campaign.name,
                        feedback.scenarioName,
                        listOf(feedback.minionId),
                        campaign.broadcastChannel
                    )
                )
            )
            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignName, feedback.scenarioName)
                RedisRunningState(
                    campaign, operations, true, listOf(
                        CampaignScenarioShutdownDirective(
                            campaign.name,
                            feedback.scenarioName,
                            campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is CampaignScenarioShutdownFeedback -> {
                if (operations.markFeedbackForScenario(feedback.campaignName, feedback.scenarioName)) {
                    context.campaignReportStateKeeper.complete(feedback.campaignName)
                    RedisCompletionState(campaign, operations)
                } else {
                    this
                }
            }
            else -> this
        }
    }
}