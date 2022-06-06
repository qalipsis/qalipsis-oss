package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.core.rampup.RampUpConfiguration

internal open class MinionsStartupState(
    protected val campaign: CampaignConfiguration
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override suspend fun doInit(): List<Directive> {
        return campaign.scenarios.keys.map { scenarioName ->
            MinionsRampUpPreparationDirective(
                campaignKey = campaignKey,
                scenarioName = scenarioName,
                rampUpConfiguration = RampUpConfiguration(campaign.startOffsetMs, campaign.speedFactor),
                channel = campaign.broadcastChannel
            )
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        // The failure management is let to doProcess.
        if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The creation of the minions for the scenario ${feedback.scenarioName} failed: ${feedback.error}" }
        } else if (feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The calculation of the minions ramping of scenario ${feedback.scenarioName} failed: ${feedback.error}" }
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED) {
            log.error { "The start of minions of scenario ${feedback.scenarioName} in the factory ${feedback.nodeId} failed: ${feedback.error}" }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.COMPLETED) {
            RunningState(campaign)
        } else {
            this
        }
    }

    override suspend fun abort(abortConfiguration: AbortCampaignConfiguration): CampaignExecutionState<CampaignExecutionContext> {
        return AbortingState(campaign, abortConfiguration, "The campaign was aborted")
    }

    override fun toString(): String {
        return "MinionsStartupState(campaign=$campaign)"
    }

    private companion object {
        val log = logger()
    }
}