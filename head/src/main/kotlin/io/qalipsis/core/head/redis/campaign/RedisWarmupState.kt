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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.NodeExecutionFeedback
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import io.qalipsis.core.head.campaign.states.CampaignExecutionContext
import io.qalipsis.core.head.campaign.states.CampaignExecutionState
import io.qalipsis.core.head.campaign.states.WarmupState
import java.time.Instant

@ExperimentalLettuceCoroutinesApi
internal class RedisWarmupState(
    campaign: RunningCampaign,
    private val operations: CampaignRedisOperations
) : WarmupState(campaign) {

    override suspend fun doInit(): List<Directive> {
        log.debug { "Initializing the status ${this::class.simpleName} for the campaign ${campaign.key}" }
        operations.setState(campaign.tenant, campaignKey, CampaignRedisState.WARMUP_STATE)
        operations.prepareAssignmentsForFeedbackExpectations(campaign)
        return super.doInit().also {
            operations.saveConfiguration(campaign)
        }
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is ScenarioWarmUpFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                RedisFailureState(campaign, feedback.error ?: "", operations)
            } else {
                if (feedback.status == FeedbackStatus.IGNORED) {
                    operations.saveConfiguration(campaign)
                }
                if (operations.markFeedbackForFactoryScenario(
                        campaign.tenant,
                        campaignKey,
                        feedback.nodeId,
                        feedback.scenarioName
                    )
                ) {
                    val start = Instant.now().plusMillis(campaign.startOffsetMs)
                    RedisRunningState(
                        campaign, operations,
                        directivesForInit = campaign.scenarios.keys.map { scenarioName ->
                            MinionsStartDirective(
                                campaignKey,
                                scenarioName,
                                start,
                                campaign.broadcastChannel
                            )
                        },
                    )
                } else {
                    this
                }
            }
        } else if (feedback is NodeExecutionFeedback && feedback.status == FeedbackStatus.FAILED) {
            // Remove the node from the campaign to avoid wait for feedbacks from it, that
            // would never come.
            campaign.factories.remove(feedback.nodeId)
            RedisFailureState(campaign, feedback.error ?: "", operations)
        } else {
            this
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return abort(campaign) {
            RedisAbortingState(campaign, abortConfiguration, "The campaign was aborted", operations)
        }
    }

    private companion object {
        val log = logger()
    }
}