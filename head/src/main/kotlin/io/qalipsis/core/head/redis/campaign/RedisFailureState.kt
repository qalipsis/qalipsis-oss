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
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.FailureState

@ExperimentalLettuceCoroutinesApi
internal class RedisFailureState(
    campaign: RunningCampaign,
    private val error: String,
    private val operations: CampaignRedisOperations
) : FailureState(campaign, error) {

    /**
     * This constructor can only be used to rebuild the state, after it was already initialized.
     */
    constructor(
        campaign: RunningCampaign,
        operations: CampaignRedisOperations
    ) : this(
        campaign,
        campaign.message,
        operations
    )

    override suspend fun doInit(): List<Directive> {
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.FAILURE_STATE)
        operations.prepareFactoriesForFeedbackExpectations(campaign)
        return super.doInit()
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignShutdownFeedback && feedback.status.isDone) {
            if (operations.markFeedbackForFactory(campaign.tenant, campaignKey, feedback.nodeId)) {
                context.campaignService.close(campaign.tenant, campaignKey, ExecutionStatus.FAILED, error)
                RedisDisabledState(campaign, false, operations)
            } else {
                this
            }
        } else {
            this
        }
    }
}