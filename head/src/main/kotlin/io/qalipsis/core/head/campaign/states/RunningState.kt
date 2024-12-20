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

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
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
import io.qalipsis.core.feedbacks.MinionsRampUpPreparationFeedback
import io.qalipsis.core.feedbacks.MinionsStartFeedback

internal open class RunningState(
    protected val campaign: RunningCampaign,
    private val directivesForInit: List<Directive> = emptyList(),
    private val expectedScenariosToComplete: MutableSet<ScenarioName> = concurrentSet(campaign.scenarios.keys)
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override suspend fun doInit(): List<Directive> {
        return directivesForInit
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        // The failure management is let to doProcess.
        when {
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The calculation of the minions ramping of scenario ${feedback.scenarioName} failed: ${feedback.error}" }

            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The start of minions of scenario ${feedback.scenarioName} in the factory ${feedback.nodeId} failed: ${feedback.error}" }

            feedback is FailedCampaignFeedback ->
                log.error { "The campaign ${feedback.campaignKey} failed in the factory ${feedback.nodeId}: ${feedback.error}" }
        }

        return doTransition(feedback)
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return when {
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }

            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }

            feedback is FailedCampaignFeedback -> {
                FailureState(campaign, feedback.error ?: "")
            }

            feedback is CompleteMinionFeedback -> {
                RunningState(
                    campaign, listOf(
                        MinionsShutdownDirective(
                            campaignKey = campaign.key,
                            scenarioName = feedback.scenarioName,
                            minionIds = listOf(feedback.minionId),
                            channel = campaign.broadcastChannel
                        )
                    ),
                    expectedScenariosToComplete = expectedScenariosToComplete
                )
            }

            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignKey, feedback.scenarioName)
                context.campaignService.closeScenario(campaign.tenant, feedback.campaignKey, feedback.scenarioName)
                RunningState(
                    campaign, listOf(
                        CampaignScenarioShutdownDirective(
                            campaignKey = campaign.key,
                            scenarioName = feedback.scenarioName,
                            channel = campaign.broadcastChannel
                        )
                    ),
                    expectedScenariosToComplete = expectedScenariosToComplete
                )
            }

            feedback is CampaignScenarioShutdownFeedback -> {
                expectedScenariosToComplete.remove(feedback.scenarioName)
                if (expectedScenariosToComplete.isEmpty()) {
                    CompletionState(campaign)
                } else {
                    log.trace { "List of remaining scenarios in the campaign ${feedback.campaignKey}: $expectedScenariosToComplete" }
                    this
                }
            }

            else -> {
                this
            }
        }
    }

    override suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<CampaignExecutionContext> {
        return abort(campaign) {
            AbortingState(campaign, abortConfiguration, "The campaign was aborted")
        }
    }

    override fun toString(): String {
        return "RunningState(campaign=$campaign, directivesForInit=$directivesForInit," +
                " expectedScenariosToComplete=$expectedScenariosToComplete)"
    }

    private companion object {
        val log = logger()
    }
}