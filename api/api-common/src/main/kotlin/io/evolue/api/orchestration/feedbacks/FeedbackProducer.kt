package io.evolue.api.orchestration.feedbacks

/**
 * Service to produce the feedbacks from factories to the heads.
 *
 * @author Eric Jessé
 */
interface FeedbackProducer {

    /**
     * Publish a directive to the heads.
     */
    suspend fun publish(feedback: Feedback)

}
