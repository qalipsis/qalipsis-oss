package io.evolue.core.cross.driving.feedback

import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId

/**
 * Notification sent from the factory to the head, to notify that the campaign was started for a DAG.
 *
 * @author Eric Jessé
 */
internal data class CampaignStartedForDagFeedback(
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
