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

package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.AbortingState
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState

@ExperimentalLettuceCoroutinesApi
internal class RedisAbortingState(
    campaign: RunningCampaign,
    abortConfiguration: AbortRunningCampaign,
    error: String,
    private val operations: CampaignRedisOperations
) : AbortingState(campaign, abortConfiguration, error) {

    /**
     * This constructor can only be used to rebuild the state, after it was already initialized.
     */
    constructor(
        campaign: RunningCampaign,
        operations: CampaignRedisOperations
    ) : this(
        campaign,
        AbortRunningCampaign(),
        campaign.message,
        operations
    )

    override suspend fun doInit(): List<Directive> {
        campaign.message = "Aborting campaign"
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.ABORTING_STATE)
        // Prepared the feedback expectations.
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit().also {
            operations.saveConfiguration(campaign)
        }
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignAbortFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaign.tenant, campaignKey, feedback.nodeId)) {
                if (abortConfiguration.hard) {
                    context.campaignService.close(
                        campaign.tenant,
                        campaignKey,
                        ExecutionStatus.ABORTED,
                        "The campaign was aborted"
                    )
                    RedisFailureState(campaign, "The campaign was aborted", operations)
                } else {
                    RedisCompletionState(campaign, operations)
                }
            } else {
                this
            }
        } else {
            this
        }
    }
}