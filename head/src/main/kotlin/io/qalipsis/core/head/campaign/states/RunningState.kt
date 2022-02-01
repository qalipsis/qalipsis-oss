package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.logging.LoggerHelper.logger
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

internal open class RunningState(
    protected val campaign: CampaignConfiguration,
    private val directivesForInit: List<Directive> = emptyList()
) : AbstractCampaignExecutionState(campaign.id) {

    override suspend fun doInit(): List<Directive> {
        return directivesForInit
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
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

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
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
                            campaign.id,
                            feedback.scenarioId,
                            listOf(feedback.minionId),
                            idGenerator.short(),
                            campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is EndOfCampaignScenarioFeedback -> {
                campaignReportStateKeeper.complete(feedback.campaignId, feedback.scenarioId)
                RunningState(
                    campaign, listOf(
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
                CompletionState(campaign)
            }
            else -> {
                this
            }
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}