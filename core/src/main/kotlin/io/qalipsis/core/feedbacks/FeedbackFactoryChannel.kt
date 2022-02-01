package io.qalipsis.core.feedbacks

/**
 * Service to produce the feedbacks from factories to the heads.
 *
 * @author Eric Jessé
 */
interface FeedbackFactoryChannel {

    /**
     * Starts the publisher of [Feedback]s to [channelName].
     */
    suspend fun start(channelName: String)

    /**
     * Publishes a feedback to the head.
     */
    suspend fun publish(feedback: Feedback)

}
