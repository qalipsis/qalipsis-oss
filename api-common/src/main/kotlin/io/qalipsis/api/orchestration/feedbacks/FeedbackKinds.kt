package io.qalipsis.api.orchestration.feedbacks

import cool.graph.cuid.Cuid
import io.qalipsis.api.orchestration.directives.DirectiveKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

typealias FeedbackKey = String

/**
 * A [Feedback] is sent from the factories to the headers to notify them of the status of an operation.
 */
@Serializable
abstract class Feedback{
    abstract val key: FeedbackKey
}

/**
 * A [DirectiveFeedback] is a [Feedback] related to a [io.qalipsis.core.cross.driving.directives.Directive].
 */
@Serializable
@SerialName("directiveFeedback")
class DirectiveFeedback(
    override val key: FeedbackKey = Cuid.createCuid(),

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
) : Feedback() {
    override fun toString(): String {
        return "DirectiveFeedback(directiveKey=$directiveKey,status=$status,error=$error)"
    }
}

enum class FeedbackStatus(val isDone: Boolean) {
    IN_PROGRESS(false), COMPLETED(true), FAILED(true)
}
