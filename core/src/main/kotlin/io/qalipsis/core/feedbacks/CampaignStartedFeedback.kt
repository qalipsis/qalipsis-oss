package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
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
    val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val dagId: DirectedAcyclicGraphName,
    /**
     * Status of the directive processing.
     */
    val status: FeedbackStatus,
    /**
     * Error message.
     */
    val error: String? = null
) : Feedback()
