/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback

internal open class MinionsStartupState(
    protected val campaign: RunningCampaign
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override suspend fun doInit(): List<Directive> {
        return campaign.scenarios.map { (scenarioName, scenarioConfiguration) ->
            MinionsRampUpPreparationDirective(
                campaignKey = campaignKey,
                scenarioName = scenarioName,
                executionProfileConfiguration = scenarioConfiguration.executionProfileConfiguration.clone(
                    startOffsetMs = campaign.startOffsetMs,
                    speedFactor = campaign.speedFactor
                ),
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

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return AbortingState(campaign, abortConfiguration, "The campaign was aborted")
    }

    override fun toString(): String {
        return "MinionsStartupState(campaign=$campaign)"
    }

    private companion object {
        val log = logger()
    }
}