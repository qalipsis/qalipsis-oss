package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignName

/**
 * Interface for [Feedback]s linked to a campaign.
 *
 * @author Eric Jessé
 */
interface CampaignManagementFeedback {
    val campaignName: CampaignName
    var nodeId: String
    val status: FeedbackStatus
    val error: String?
    var tenant: String
}