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
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.head.model.Factory

internal open class FactoryAssignmentState(
    protected val campaign: RunningCampaign,
    private val factories: Collection<Factory>,
    private val scenarios: Collection<ScenarioSummary>
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    private val expectedFeedbacks = concurrentSet<NodeId>()

    override suspend fun doInit(): List<Directive> {
        // Locks all the factories from a concurrent assignment resolution.
        if (campaign.factories.isEmpty()) {
            context.assignmentResolver.assignFactories(campaign, factories, scenarios)
        }

        expectedFeedbacks.addAll(campaign.factories.keys)

        // Creates one directive by factory to notify its assignments.
        return campaign.factories.map { (factoryId, configuration) ->
            val factory = campaign.factories[factoryId]!!
            FactoryAssignmentDirective(
                campaignKey = campaign.key,
                assignments = configuration.assignment.values,
                runningCampaign = campaign,
                channel = factory.unicastChannel
            )
        }
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        if (feedback is FactoryAssignmentFeedback) {
            if (feedback.status == FeedbackStatus.IGNORED) {
                campaign.unassignFactory(feedback.nodeId)
                context.factoryService.releaseFactories(campaign, listOf(feedback.nodeId))
            } else if (feedback.status == FeedbackStatus.FAILED) {
                // The failure management is let to doProcess.
                log.error { "The assignment of DAGs to the factory ${feedback.nodeId} failed: ${feedback.error}" }
            }
        }
        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is FactoryAssignmentFeedback && feedback.status.isDone) {
            if (feedback.status == FeedbackStatus.FAILED) {
                FailureState(campaign, feedback.error ?: "")
            } else {
                expectedFeedbacks -= feedback.nodeId
                if (expectedFeedbacks.isEmpty()) {
                    MinionsAssignmentState(campaign)
                } else {
                    this
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
        return "FactoryAssignmentState(campaign=$campaign, expectedFeedbacks=$expectedFeedbacks)"
    }

    private companion object {
        val log = logger()
    }
}