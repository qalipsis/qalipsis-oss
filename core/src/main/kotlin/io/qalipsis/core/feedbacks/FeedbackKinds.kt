package io.qalipsis.core.feedbacks

import cool.graph.cuid.Cuid
import io.qalipsis.core.directives.DirectiveKey
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
abstract class Feedback {
    abstract val key: FeedbackKey
}

/**
 * A [DirectiveFeedback] is a [Feedback] related to a [io.qalipsis.core.cross.driving.directives.Directive].
 */
@Serializable
@SerialName("directiveFeedback")
data class DirectiveFeedback(
    override val key: FeedbackKey = Cuid.createCuid(),

    /**
     * Key of the directive when the feedback is relevant to one.
     */
    val directiveKey: DirectiveKey,

    /**
     * identifier of the factory that emitted the feedback, when known.
     */
    val nodeId: String = "",

    /**
     * Status of the directive processing.
     */
    val status: FeedbackStatus,

    /**
     * Error message.
     */
    val error: String? = null
) : Feedback() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DirectiveFeedback

        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}

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
