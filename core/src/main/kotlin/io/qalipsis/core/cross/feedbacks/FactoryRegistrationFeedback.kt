package io.qalipsis.core.cross.feedbacks

import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.core.heads.campaigns.HeadDirectedAcyclicGraph
import io.qalipsis.core.heads.campaigns.HeadScenario

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
