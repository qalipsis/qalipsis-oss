package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
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
    campaign: RunningCampaign,
    private val operations: CampaignRedisOperations,
    private val doNotPersistStateOnInit: Boolean = false,
    directivesForInit: List<Directive> = emptyList()
) : RunningState(campaign, directivesForInit) {

    override suspend fun doInit(): List<Directive> {
        if (!doNotPersistStateOnInit) {
            operations.setState(campaign.tenant, campaignKey, CampaignRedisState.RUNNING_STATE)
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
                        campaign.key,
                        feedback.scenarioName,
                        listOf(feedback.minionId),
                        campaign.broadcastChannel
                    )
                )
            )
            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignKey, feedback.scenarioName)
                context.campaignService.closeScenario(campaign.tenant, feedback.campaignKey, feedback.scenarioName)
                RedisRunningState(
                    campaign, operations, true, listOf(
                        CampaignScenarioShutdownDirective(
                            campaign.key,
                            feedback.scenarioName,
                            campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is CampaignScenarioShutdownFeedback -> {
                if (operations.markFeedbackForScenario(campaign.tenant, feedback.campaignKey, feedback.scenarioName)) {
                    context.campaignReportStateKeeper.complete(feedback.campaignKey)
                    RedisCompletionState(campaign, operations)
                } else {
                    this
                }
            }
            else -> this
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return RedisAbortingState(campaign, abortConfiguration, "The campaign was aborted", operations)
    }
}