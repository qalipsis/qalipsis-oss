package io.qalipsis.core.feedbacks

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus

/**
 * Notification sent from the factory to the head, to notify that the campaign was started for a DAG.
 *
 * @author Eric Jessé
 */
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
