package io.qalipsis.core.feedbacks

import io.qalipsis.core.feedbacks.FeedbackStatus.COMPLETED
import io.qalipsis.core.feedbacks.FeedbackStatus.FAILED
import io.qalipsis.core.feedbacks.FeedbackStatus.IGNORED
import io.qalipsis.core.feedbacks.FeedbackStatus.IN_PROGRESS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias FeedbackKey = String

/**
 * A [Feedback] is sent from the factories to the headers to notify them of the status of an operation.
 */
@Serializable
abstract class Feedback

/**
 * A [DirectiveFeedback] is a [Feedback] related to a [io.qalipsis.core.cross.driving.directives.Directive].
 */
@Serializable
@SerialName("directiveFeedback")
data class DirectiveFeedback(

    /**
     * identifier of the factory that emitted the feedback, when known.
     */
    var nodeId: String = "",

    /**
     * Status of the directive processing.
     */
    val status: FeedbackStatus,

    /**
     * Error message.
     */
    val error: String? = null,

    /**
     * reference to the tenant of the factory that emitted the feedback, when known.
     */
    var tenant: String = ""
) : Feedback()

/**
 * Status of a feedback to a task.
 *
 * @property IN_PROGRESS the task is currently being processed by its executor
 * @property IGNORED the task is not relevant for its executor
 * @property COMPLETED the task was completed successfully
 * @property FAILED the task execution failed
 *
 * @author Eric Jessé
 */
enum class FeedbackStatus(val isDone: Boolean) {
    IN_PROGRESS(false),
    IGNORED(true),
    COMPLETED(true),
    FAILED(true)
}
