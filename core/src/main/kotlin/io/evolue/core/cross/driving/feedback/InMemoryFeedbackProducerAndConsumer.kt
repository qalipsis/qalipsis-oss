package io.evolue.core.cross.driving.feedback

import cool.graph.cuid.Cuid
import io.evolue.api.messaging.TopicMode
import io.evolue.api.messaging.topic
import io.evolue.core.cross.configuration.ENV_STANDALONE
import io.micronaut.context.annotation.Requires
import kotlinx.coroutines.channels.Channel
import javax.inject.Singleton

/**
 * Implementation of [FeedbackProducer] and [FeedbackConsumer] distributing the [Feedback]s via a [Channel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
internal class InMemoryFeedbackProducerAndConsumer : FeedbackProducer, FeedbackConsumer {

    private val topic = topic(TopicMode.BROADCAST, fromBeginning = true)

    override suspend fun publish(feedback: Feedback) {
        topic.produce(feedback)
    }

    override suspend fun onReceive(block: suspend (Feedback) -> Unit) {
        return topic.subscribe(Cuid.createCuid()).onReceiveValue(block)
    }

}
