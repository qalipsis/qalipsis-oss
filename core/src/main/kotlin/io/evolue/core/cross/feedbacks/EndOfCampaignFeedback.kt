package io.evolue.core.cross.feedbacks

import io.evolue.api.context.CampaignId
import io.evolue.api.orchestration.feedbacks.Feedback

/**
 * Notification sent from the factory to the head, to notify that all standards minions have executed their steps.
 *
 * @author Eric Jessé
 */
internal data class EndOfCampaignFeedback(
    val campaignId: CampaignId
) : Feedback()
