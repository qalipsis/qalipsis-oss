package io.qalipsis.core.feedbacks

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [FeedbackProducer] and [FeedbackConsumer] distributing the [Feedback]s via a [Channel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [STANDALONE])
internal class InMemoryFeedbackProducerAndConsumer : FeedbackProducer, FeedbackConsumer {

    private val topic = broadcastTopic<Feedback>()

    @LogInputAndOutput
    override suspend fun publish(feedback: Feedback) {
        topic.produceValue(feedback)
    }

    @LogInputAndOutput
    override suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job {
        return topic.subscribe(subscriberId).onReceiveValue(block)
    }

}
