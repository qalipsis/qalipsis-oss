package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification sent from the factory to the head, when the execution of all the scenarios is complete.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("fail")
data class FailedCampaignFeedback(
    override val campaignId: CampaignId,
    override val error: String
) : Feedback(), CampaignManagementFeedback {

    override var nodeId: String = ""

    override val status: FeedbackStatus = FeedbackStatus.FAILED

}
