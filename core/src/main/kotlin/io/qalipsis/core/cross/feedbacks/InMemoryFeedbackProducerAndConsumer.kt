package io.qalipsis.core.cross.feedbacks

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
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
internal class InMemoryFeedbackProducerAndConsumer(
    private val idGenerator: IdGenerator
) : FeedbackProducer, FeedbackConsumer {

    private val topic = broadcastTopic<Feedback>()

    override suspend fun publish(feedback: Feedback) {
        topic.produceValue(feedback)
    }

    override suspend fun onReceive(block: suspend (Feedback) -> Unit): Job {
        return topic.subscribe(idGenerator.short()).onReceiveValue(block)
    }

}
