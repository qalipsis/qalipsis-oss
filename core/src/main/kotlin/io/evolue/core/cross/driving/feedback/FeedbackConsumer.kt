package io.evolue.core.cross.driving.feedback

import kotlinx.coroutines.flow.Flow

/**
 * Service for the head to consume the feedbacks from factories.
 *
 * @author Eric Jessé
 */
internal interface FeedbackConsumer {

    suspend fun subscribe(): Flow<Feedback>

}