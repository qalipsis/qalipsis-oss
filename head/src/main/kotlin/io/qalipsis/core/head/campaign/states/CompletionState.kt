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
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback

open class CompletionState(
    protected val campaign: RunningCampaign
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        return listOf(
            CampaignShutdownDirective(
                campaignKey = campaignKey,
                channel = campaign.broadcastChannel
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
                context.campaignReportStateKeeper.complete(campaignKey, ExecutionStatus.SUCCESSFUL)
                context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.SUCCESSFUL)
                DisabledState(campaign)
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "CompletionState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

}