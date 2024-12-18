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

import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus

internal open class FailureState(
    protected val campaign: RunningCampaign,
    private val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    init {
        if (error.isNotBlank()) {
            campaign.message = error
        }
    }

    override suspend fun doInit(): List<Directive> {
        // In case the factories do not answer fast enough or not at all,
        // schedule a failure on each node to go on with the next state.
        campaign.factories.keys.forEach { nodeId ->
            context.delayedFeedbackManager.scheduleCancellation(
                campaign.feedbackChannel, CampaignShutdownFeedback(
                    campaign.key,
                    status = FeedbackStatus.FAILED,
                    errorMessage = "The factory could not be properly stopped"
                ).also {
                    it.nodeId = nodeId
                    it.tenant = campaign.tenant
                })
        }

        return listOf(
            CampaignShutdownDirective(
                campaignKey = campaignKey,
                channel = campaign.broadcastChannel
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            if (expectedFeedbacks.remove(feedback.nodeId) && feedback.status == FeedbackStatus.FAILED) {
                log.error { "Properly shutting down the factory ${feedback.nodeId} failed, please proceed manually." }
            }
            if (expectedFeedbacks.isEmpty()) {
                context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.FAILED, error)
                DisabledState(campaign, false)
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "FailureState(campaign=$campaign, error='$error', expectedFeedbacks=$expectedFeedbacks)"
    }

    private companion object {
        val log = logger()
    }

}