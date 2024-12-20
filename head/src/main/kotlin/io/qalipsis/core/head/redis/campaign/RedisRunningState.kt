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
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
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
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.RunningState

@ExperimentalLettuceCoroutinesApi
internal class RedisRunningState(
    campaign: RunningCampaign,
    private val operations: CampaignRedisOperations,
    private val doNotPersistStateOnInit: Boolean = false,
    directivesForInit: List<Directive> = emptyList()
) : RunningState(campaign, directivesForInit) {

    override suspend fun doInit(): List<Directive> {
        if (!doNotPersistStateOnInit) {
            operations.setState(campaign.tenant, campaignKey, CampaignRedisState.RUNNING_STATE)
            operations.prepareScenariosForFeedbackExpectations(campaign)
        }
        return super.doInit().also {
            operations.saveConfiguration(campaign)
        }
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return when {
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)

            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)

            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED ->
                RedisFailureState(campaign, feedback.error ?: "", operations)

            feedback is FailedCampaignFeedback -> RedisFailureState(
                campaign,
                feedback.error ?: "",
                operations
            )

            feedback is CompleteMinionFeedback -> RedisRunningState(
                campaign, operations, true, listOf(
                    MinionsShutdownDirective(
                        campaign.key,
                        feedback.scenarioName,
                        listOf(feedback.minionId),
                        campaign.broadcastChannel
                    )
                )
            )

            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignKey, feedback.scenarioName)
                context.campaignService.closeScenario(campaign.tenant, feedback.campaignKey, feedback.scenarioName)
                RedisRunningState(
                    campaign, operations, true, listOf(
                        CampaignScenarioShutdownDirective(
                            campaign.key,
                            feedback.scenarioName,
                            campaign.broadcastChannel
                        )
                    )
                )
            }

            feedback is CampaignScenarioShutdownFeedback -> {
                if (operations.markFeedbackForScenario(campaign.tenant, feedback.campaignKey, feedback.scenarioName)) {
                    RedisCompletionState(campaign, operations)
                } else {
                    this
                }
            }

            else -> this
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return abort(campaign) {
            RedisAbortingState(campaign, abortConfiguration, "The campaign was aborted", operations)
        }
    }
}