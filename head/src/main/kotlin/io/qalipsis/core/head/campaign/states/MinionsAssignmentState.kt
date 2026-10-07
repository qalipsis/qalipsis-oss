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

import io.qalipsis.api.context.NodeId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

open class MinionsAssignmentState(
    protected val campaign: RunningCampaign
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks =
        ConcurrentHashMap(campaign.factories.mapValues { it.value.assignment.keys.toSet() })

    private val mutex = Mutex(false)

    /**
     * Counts of minions under load effectively assigned to each factory, for each scenario.
     */
    private val assignedMinionsUnderLoadCounts =
        ConcurrentHashMap<ScenarioName, MutableMap<NodeId, Int>>()

    override suspend fun doInit(): List<Directive> {
        return campaign.scenarios.map { (scenarioName, config) ->
            MinionsDeclarationDirective(
                campaignKey = campaignKey,
                scenarioName = scenarioName,
                minionsCount = config.minionsCount,
                channel = campaign.broadcastChannel
            )
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        if (feedback is MinionsDeclarationFeedback) {
            if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The declaration of minions failed in the factory ${feedback.nodeId}: ${feedback.error}" }
            }
        } else if (feedback is MinionsAssignmentFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignScenarioOfFactory(feedback.scenarioName, feedback.nodeId)
                if (feedback.nodeId !in campaign) {
                    context.factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
                }
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of minions to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            } else if (feedback.status == FeedbackStatus.COMPLETED) {
                assignedMinionsUnderLoadCounts.computeIfAbsent(feedback.scenarioName) { ConcurrentHashMap() }[feedback.nodeId] =
                    feedback.assignedMinionsUnderLoadCount
            }
        }
        return doTransition(feedback).also { newState ->
            // The assignments are complete when the state changes to let the ramp-up be prepared.
            if (newState is MinionsScheduleRampUpState) {
                verifyAssignmentsCompleteness()
            }
        }
    }

    /**
     * Reports the minions that no factory could assign to itself, hence that will never be executed.
     *
     * Each factory can only assign the count of minions it was allowed to, so a factory that is missing or that
     * failed to claim its share leaves minions behind, without any other factory being able to execute them.
     */
    private fun verifyAssignmentsCompleteness() {
        campaign.scenarios.forEach { (scenarioName, configuration) ->
            val countsByFactory = assignedMinionsUnderLoadCounts[scenarioName].orEmpty()
            val assignedMinionsCount = countsByFactory.values.sum()
            if (assignedMinionsCount < configuration.minionsCount) {
                log.error {
                    "Only $assignedMinionsCount minions of the ${configuration.minionsCount} of the scenario $scenarioName were assigned for the campaign $campaignKey, the ${configuration.minionsCount - assignedMinionsCount} remaining ones will not be executed, assignments by factory: $countsByFactory"
                }
            }
        }
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED) {
            FailureState(campaign, feedback.error ?: "")
        } else if (feedback is MinionsAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                mutex.withLock {
                    expectedFeedbacks.computeIfPresent(feedback.nodeId) { _, scenarios ->
                        (scenarios - feedback.scenarioName).ifEmpty { null }
                    }
                    if (expectedFeedbacks.isEmpty()) {
                        MinionsScheduleRampUpState(campaign)
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
        return abort(campaign) {
            AbortingState(campaign, abortConfiguration, "The campaign was aborted")
        }
    }

    override fun toString(): String {
        return "MinionsAssignmentState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

    private companion object {
        val log = logger()
    }
}