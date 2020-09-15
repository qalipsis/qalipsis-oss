package io.evolue.core.cross.driving.feedback

import kotlinx.coroutines.Job

/**
 * Service for the head to consume the feedbacks from factories.
 *
 * @author Eric Jessé
 */
internal interface FeedbackConsumer {

    suspend fun onReceive(block: suspend (Feedback) -> Unit): Job

}
