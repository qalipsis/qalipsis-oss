package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification sent from the factory to the head, to notify that the campaign was started for a DAG.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("csfd")
data class CampaignStartedForDagFeedback(
    val campaignId: CampaignId,
    val scenarioId: ScenarioId,
    val dagId: DirectedAcyclicGraphId,
    /**
     * Status of the directive processing.
     */
    val status: FeedbackStatus,
    /**
     * Error message.
     */
    val error: String? = null
) : Feedback()
