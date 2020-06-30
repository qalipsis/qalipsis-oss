package io.evolue.core.cross.driving.feedback

import io.evolue.api.context.CampaignId

/**
 * Notification sent from the factory to the head, to notify that all standards minions have executed their steps.
 *
 * @author Eric Jessé
 */
internal data class EndOfCampaignFeedback(
        val campaignId: CampaignId
) : Feedback()
