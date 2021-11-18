package io.qalipsis.core.feedbacks

import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary

/**
 * Notification sent from the factory to the head, when the factory has prepared its scenario and is ready for work.
 *
 * @author Eric Jessé
 */
data class FactoryRegistrationFeedback(
    val scenarios: List<FactoryRegistrationFeedbackScenario>
) : Feedback()

typealias FactoryRegistrationFeedbackScenario = ScenarioSummary
typealias FactoryRegistrationFeedbackDirectedAcyclicGraph = DirectedAcyclicGraphSummary
