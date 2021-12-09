package io.qalipsis.core.feedbacks

import cool.graph.cuid.Cuid
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification sent from the factory to the head, to notify that all standards minions have executed their steps.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("eocf")
data class EndOfCampaignFeedback(
    val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    override val key: FeedbackKey = Cuid.createCuid()
) : Feedback()
