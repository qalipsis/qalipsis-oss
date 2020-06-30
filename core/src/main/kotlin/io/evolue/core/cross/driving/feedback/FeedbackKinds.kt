package io.evolue.core.cross.driving.feedback

import cool.graph.cuid.Cuid
import io.evolue.core.cross.driving.directives.DirectiveKey

internal typealias FeedbackKey = String

/**
 * A [Feedback] is sent from the factories to the headers to notify them of the status of an operation.
 */
internal abstract class Feedback(
        val key: FeedbackKey = Cuid.createCuid()
)

/**
 * A [DirectiveFeedback] is a [Feedback] related to a [io.evolue.core.cross.driving.directives.Directive].
 */
internal class DirectiveFeedback(
        key: FeedbackKey = Cuid.createCuid(),

        /**
         * Key of the directive when the feedback is relevant to one.
         */
        val directiveKey: DirectiveKey,

        /**
         * Status of the directive processing.
         */
        val status: FeedbackStatus,

        /**
         * Error message.
         */
        val error: String? = null
) : Feedback(key) {
    override fun toString(): String {
        return "DirectiveFeedback(directiveKey=$directiveKey,status=$status,error=$error)"
    }
}

internal enum class FeedbackStatus(val isDone: Boolean) {
    IN_PROGRESS(false), COMPLETED(true), FAILED(true)
}
