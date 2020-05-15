package io.evolue.core.cross.driving.feedback

import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [FeedbackProducer] publishing the [Feedback]s via a [Channel], used for deployments
 * where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
internal class InMemoryFeedbackProducer : FeedbackProducer {

    val channel = Channel<Feedback>(Channel.BUFFERED)

    override suspend fun publish(feedback: Feedback) {
        channel.send(feedback)
    }

}