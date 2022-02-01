package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId

/**
 * Interface for [Feedback]s linked to a campaign.
 *
 * @author Eric Jessé
 */
interface CampaignManagementFeedback {
    val campaignId: CampaignId
    val nodeId: String
    val status: FeedbackStatus
    val error: String?
}