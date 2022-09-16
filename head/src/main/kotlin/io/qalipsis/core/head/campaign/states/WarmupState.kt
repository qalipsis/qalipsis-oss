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
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.ScenarioWarmUpFeedback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal open class WarmupState(
    protected val campaign: RunningCampaign
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks =
        ConcurrentHashMap(campaign.factories.mapValues { it.value.assignment.keys.toSet() })

    private val mutex = Mutex(false)

    override suspend fun doInit(): List<Directive> {
        return campaign.factories.values.flatMap { config ->
            config.assignment.keys.map { scenarioName ->
                ScenarioWarmUpDirective(
                    campaignKey = campaignKey,
                    scenarioName = scenarioName,
                    channel = config.unicastChannel
                )
            }
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        if (feedback is ScenarioWarmUpFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignScenarioOfFactory(feedback.scenarioName, feedback.nodeId)
                if (feedback.nodeId !in campaign) {
                    context.factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
                }
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of minions to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is ScenarioWarmUpFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                mutex.withLock {
                    expectedFeedbacks.computeIfPresent(feedback.nodeId) { _, scenarios ->
                        (scenarios - feedback.scenarioName).ifEmpty { null }
                    }
                    if (expectedFeedbacks.isEmpty()) {
                        MinionsStartupState(campaign)
                    } else {
                        this
                    }
                }
            }
        } else {
            this
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return AbortingState(campaign, abortConfiguration, "The campaign was aborted")
    }

    override fun toString(): String {
        return "WarmupState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

    private companion object {
        val log = logger()
    }
}