package io.qalipsis.core.cross.feedbacks

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [FeedbackProducer] and [FeedbackConsumer] distributing the [Feedback]s via a [Channel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
internal class InMemoryFeedbackProducerAndConsumer(
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineContext: CoroutineContext
) : FeedbackProducer, FeedbackConsumer {

    private val topic = broadcastTopic<Feedback>()

    @LogInputAndOutput
    override suspend fun publish(feedback: Feedback) {
        topic.produceValue(feedback)
    }

    @LogInputAndOutput
    override suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job {
        return topic.subscribe(subscriberId, coroutineContext).onReceiveValue(coroutineContext, block)
    }

}
