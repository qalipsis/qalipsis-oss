package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.AbortCampaignConfiguration
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

internal open class RunningState(
    protected val campaign: CampaignConfiguration,
    private val directivesForInit: List<Directive> = emptyList(),
    private val expectedScenariosToComplete: MutableSet<ScenarioName> = concurrentSet(campaign.scenarios.keys)
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.key) {

    override suspend fun doInit(): List<Directive> {
        return directivesForInit
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        // The failure management is let to doProcess.
        when {
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED ->
                log.error { "The creation of the minions for the scenario ${feedback.scenarioName} failed: ${feedback.error}" }
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
            feedback is MinionsDeclarationFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is MinionsRampUpPreparationFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is MinionsStartFeedback && feedback.status == FeedbackStatus.FAILED -> {
                FailureState(campaign, feedback.error ?: "")
            }
            feedback is FailedCampaignFeedback -> {
                FailureState(campaign, feedback.error)
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
                    )
                )
            }
            feedback is EndOfCampaignScenarioFeedback -> {
                context.campaignReportStateKeeper.complete(feedback.campaignKey, feedback.scenarioName)
                RunningState(
                    campaign, listOf(
                        CampaignScenarioShutdownDirective(
                            campaignKey = campaign.key,
                            scenarioName = feedback.scenarioName,
                            channel = campaign.broadcastChannel
                        )
                    )
                )
            }
            feedback is CampaignScenarioShutdownFeedback -> {
                expectedScenariosToComplete.remove(feedback.scenarioName)
                if (expectedScenariosToComplete.isEmpty()) {
                    context.campaignReportStateKeeper.complete(feedback.campaignKey)
                    CompletionState(campaign)
                } else {
                    this
                }
            }
            else -> {
                this
            }
        }
    }

    override suspend fun abort(abortConfiguration: AbortCampaignConfiguration): CampaignExecutionState<CampaignExecutionContext> {
        return AbortingState(campaign, abortConfiguration, "The campaign was aborted")
    }

    override fun toString(): String {
        return "RunningState(campaign=$campaign, directivesForInit=$directivesForInit," +
                " expectedScenariosToComplete=$expectedScenariosToComplete)"
    }

    private companion object {
        val log = logger()
    }
}