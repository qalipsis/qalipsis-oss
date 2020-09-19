package io.evolue.core.cross.feedbacks

import io.evolue.api.orchestration.feedbacks.Feedback
import io.evolue.core.heads.campaigns.HeadDirectedAcyclicGraph
import io.evolue.core.heads.campaigns.HeadScenario

/**
 * Notification sent from the factory to the head, when the factory has prepared its scenario and is ready for work.
 *
 * @author Eric Jessé
 */
internal data class FactoryRegistrationFeedback(
    val scenarios: List<FactoryRegistrationFeedbackScenario>
) : Feedback()

internal typealias FactoryRegistrationFeedbackScenario = HeadScenario
internal typealias FactoryRegistrationFeedbackDirectedAcyclicGraph = HeadDirectedAcyclicGraph
