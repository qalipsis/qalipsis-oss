package io.evolue.core.cross.driving.feedback

/**
 * Service to produce the feedbacks from factories to the heads.
 *
 * @author Eric Jessé
 */
internal interface FeedbackProducer {

    /**
     * Publish a directive to the heads.
     */
    suspend fun publish(feedback: Feedback)

}