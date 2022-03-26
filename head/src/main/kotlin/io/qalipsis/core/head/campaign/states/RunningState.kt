package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
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

internal open class RunningState(
    protected val campaign: CampaignConfiguration,
    private val directivesForInit: List<Directive> = emptyList(),
    private val expectedScenariosToComplete: MutableSet<ScenarioId> = concurrentSet(campaign.scenarios.keys)
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.id) {

    override suspend fun doInit(): List<Directive> {
        return directivesForInit
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        // The failure management is let to doProcess.
        when {
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The creation of the minions for the scenario ${feedback.scenarioId} failed: ${feedback.error}" }
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The calculation of the minions ramping of scenario ${feedback.scenarioId} failed: ${feedback.error}" }
            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The start of minions of scenario ${feedback.scenarioId} in the factory ${feedback.nodeId} failed: ${feedback.error}" }
            feedback is FailedCampaignFeedback ->
                log.error { "The campaign ${feedback.campaignId} failed in the factory ${feedback.nodeId}: ${feedback.error}" }
        }

        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return when {
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is FailedCampaignFeedback -> {
                FailureState(campaign, feedback.error)
            }
            feedback is CompleteMinionFeedback -> {
                RunningState(
                    campaign, listOf(
                        MinionsShutdownDirective(
                            campaignId = campaign.id,
                            scenarioId = feedback.scenarioId,
                            minionIds = listOf(feedback.minionId),
                            channel = campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignId, feedback.scenarioId)
                RunningState(
                    campaign, listOf(
                        CampaignScenarioShutdownDirective(
                            campaignId = campaign.id,
                            scenarioId = feedback.scenarioId,
                            channel = campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is CampaignScenarioShutdownFeedback -> {
                expectedScenariosToComplete.remove(feedback.scenarioId)
                if (expectedScenariosToComplete.isEmpty()) {
                    context.campaignReportStateKeeper.complete(feedback.campaignId)
                    CompletionState(campaign)
                } else {
                    this
                }
            }
            else -> {
                this
            }
        }
    }

    override fun toString(): String {
        return "RunningState(campaign=$campaign, directivesForInit=$directivesForInit, expectedScenariosToComplete=$expectedScenariosToComplete)"
    }


    private companion object {

        @JvmStatic
        val log = logger()
    }
}