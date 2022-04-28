package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.configuration.AbortCampaignConfiguration
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.CampaignAbortFeedback
import io.qalipsis.core.feedbacks.Feedback

internal open class AbortingState(
    protected val campaign: CampaignConfiguration,
    protected val abortConfiguration: AbortCampaignConfiguration,
    protected val error: String
) : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign.name) {

    private val expectedFeedbacks = concurrentSet(campaign.factories.keys)

    override suspend fun doInit(): List<Directive> {
        campaign.message = "Aborting campaign"
        return listOf(
            CampaignAbortDirective(
                campaignName = campaignName,
                channel = campaign.broadcastChannel,
                scenarioNames = campaign.scenarios.keys.toList()
            )
        )
    }

    override suspend fun doTransition(feedback: Feedback): CampaignExecutionState<CampaignExecutionContext> {
        return if (feedback is CampaignAbortFeedback && feedback.status.isDone) {
            expectedFeedbacks -= feedback.nodeId
            if (expectedFeedbacks.isEmpty()) {
                if (abortConfiguration.hard) {
                    FailureState(campaign, "The campaign was aborted")
                } else {
                    CompletionState(campaign)
                }
            } else {
                this
            }
        } else {
            this
        }
    }

    override fun toString(): String {
        return "AbortingState(campaign=$campaign, abortConfiguration = $abortConfiguration," +
                " error='$error', expectedFeedbacks=$expectedFeedbacks)"
    }
}