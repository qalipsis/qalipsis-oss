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
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class AbortingState(
    protected val campaign: RunningCampaign,
    protected val abortConfiguration: AbortRunningCampaign,
    protected val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        campaign.message = "Aborting campaign"
        return listOf(
            CampaignAbortDirective(
                campaignKey = campaignKey,
                channel = campaign.broadcastChannel,
                scenarioNames = campaign.scenarios.keys.toList(),
                abortRunningCampaign = AbortRunningCampaign(abortConfiguration.hard)
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignAbortFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
                if (abortConfiguration.hard) {
                    context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.ABORTED)
                    FailureState(campaign, "The campaign was aborted")
                } else {
                    CompletionState(campaign)
                }
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "AbortingState(campaign=$campaign, abortConfiguration = $abortConfiguration," +
                " error='$error', expectedFeedbacks=$expectedFeedbacks)"
    }
}