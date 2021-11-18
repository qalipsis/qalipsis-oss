package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.feedbacks.Feedback

/**
 * Notification sent from the factory to the head, to notify that all standards minions have executed their steps.
 *
 * @author Eric Jessé
 */
data class EndOfCampaignFeedback(
    val campaignId: CampaignId,
    val scenarioId: ScenarioId
) : Feedback()
