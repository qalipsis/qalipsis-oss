package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.rampup.RampUpConfiguration

internal open class MinionsStartupState(
    protected val campaign: CampaignConfiguration
) : AbstractCampaignExecutionState(campaign.id) {

    override suspend fun doInit(): List<Directive> {
        return campaign.scenarios.keys.map { scenarioId ->
            MinionsRampUpPreparationDirective(
                campaignId,
                scenarioId,
                RampUpConfiguration(campaign.startOffsetMs, campaign.speedFactor),
                idGenerator.short(),
                campaign.broadcastChannel
            )
        }
    }

    override suspend fun doTransition(directive: Directive): CampaignExecutionState {
        return if (directive is MinionsStartDirective) {
            RunningState(campaign)
        } else {
            this
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
        // The failure management is let to doProcess.
        if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The creation of the minions for the scenario ${feedback.scenarioId} failed: ${feedback.error}" }
        } else if (feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The calculation of the minions ramping of scenario ${feedback.scenarioId} failed: ${feedback.error}" }
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The start of minions of scenario ${feedback.scenarioId} in the factory ${feedback.nodeId} failed: ${feedback.error}" }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else {
            this
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}