package io.qalipsis.api.orchestration.feedbacks

import kotlinx.coroutines.Job

/**
 * Service for the head to consume the feedbacks from factories.
 *
 * @author Eric Jessé
 */
interface FeedbackConsumer {

    suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job

}
