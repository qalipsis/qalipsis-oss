package io.qalipsis.core.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import javax.annotation.PreDestroy

/**
 * Implementation of [FeedbackHeadChannel] and [FeedbackFactoryChannel] distributing the [Feedback]s via a [Channel],
 * used for deployments where the head and a unique factory run in the same JVM.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class InMemoryFeedbackChannel : FeedbackHeadChannel, FeedbackFactoryChannel {

    private val topic = broadcastTopic<Feedback>()

    override suspend fun start(channelName: String) = Unit

    @LogInputAndOutput
    override suspend fun publish(feedback: Feedback) {
        topic.produceValue(feedback)
    }

    override suspend fun subscribe(channelName: String) = Unit

    @LogInputAndOutput
    override suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job {
        return topic.subscribe(subscriberId).onReceiveValue(block)
    }

    @PreDestroy
    fun close() {
        kotlin.runCatching {
            topic.close()
        }
    }
}