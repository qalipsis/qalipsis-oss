package io.evolue.core.cross.driving.feedback

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * Implementation of [FeedbackProducer] and [FeedbackConsumer] distributing the [Feedback]s via a [Channel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
internal class InMemoryFeedbackProducerAndConsumer : FeedbackProducer, FeedbackConsumer {

    val channel = BroadcastChannel<Feedback>(Channel.BUFFERED)

    override suspend fun publish(feedback: Feedback) {
        channel.send(feedback)
    }

    override suspend fun subscribe(): Flow<Feedback> {
        return channel.openSubscription().consumeAsFlow()
    }

}