package io.qalipsis.core.feedbacks

import kotlinx.coroutines.Job

/**
 * Service for the head to consume the feedbacks from factories.
 *
 * @author Eric Jessé
 */
interface FeedbackHeadChannel {

    /**
     * Starts to subscribe to [Feedback]s from channel [channelName].
     */
    suspend fun subscribe(channelName: String)

    suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job

}
