package io.qalipsis.core.head.communication

import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeResponse

/**
 * A [FeedbackListener] receives [HandshakeResponse]s for processing.
 *
 * @author Eric Jessé
 */
interface FeedbackListener<D : Feedback> {

    /**
     * Returns {@code true} when the listener can work with the feedback.
     */
    fun accept(feedback: Feedback): Boolean

    /**
     * Processes the [Feedback].
     */
    suspend fun notify(feedback: D)
}
