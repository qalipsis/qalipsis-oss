package io.evolue.core.cross.feedbacks

import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.orchestration.feedbacks.Feedback
import io.evolue.api.orchestration.feedbacks.FeedbackStatus

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
