package io.evolue.core.cross.feedbacks

import cool.graph.cuid.Cuid
import io.evolue.api.orchestration.feedbacks.Feedback
import io.evolue.api.orchestration.feedbacks.FeedbackConsumer
import io.evolue.api.orchestration.feedbacks.FeedbackProducer
import io.evolue.api.messaging.broadcastTopic
import io.evolue.core.cross.configuration.ENV_STANDALONE
import io.micronaut.context.annotation.Requires
import kotlinx.coroutines.Job
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

    private val topic = broadcastTopic<Feedback>()

    override suspend fun publish(feedback: Feedback) {
        topic.produceValue(feedback)
    }

    override suspend fun onReceive(block: suspend (Feedback) -> Unit): Job {
        return topic.subscribe(Cuid.createCuid()).onReceiveValue(block)
    }

}
