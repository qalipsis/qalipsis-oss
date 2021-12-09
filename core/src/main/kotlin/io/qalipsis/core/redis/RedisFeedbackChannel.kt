package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PreDestroy

/**
 * Implementation of [FeedbackHeadChannel] and [FeedbackFactoryChannel] distributing the [Feedback]s via Redis,
 * used for deployments other than [STANDALONE] mode.
 *
 * @property serializer Serializer for redis messages.
 * @property idGenerator Id generator.
 * @property coroutineScope Coroutine scope for execution of jobs.
 * @property redisCommands Redis Coroutines commands.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requires(notEnv = [STANDALONE])
@ExperimentalLettuceCoroutinesApi 
internal class RedisFeedbackChannel(
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val serializer: DistributionSerializer,
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator
) : FeedbackHeadChannel, FeedbackFactoryChannel {

    private lateinit var producerChannelName: String
    private lateinit var consumerChannelName: String
    private val receivingJobs: MutableList<Job> = mutableListOf()
    private var redisConsumerClients: MutableList<RedisConsumerClient<Feedback>> = mutableListOf()

    override suspend fun start(channelName: String) {
        this.producerChannelName = channelName
    }

    @LogInputAndOutput
    override suspend fun publish(feedback: Feedback) {
        redisCommands.xadd(producerChannelName, mapOf(feedback.key to serializer.serialize(feedback).decodeToString()))
    }

    override suspend fun subscribe(channelName: String) {
        consumerChannelName = channelName
    }

    @LogInputAndOutput
    override suspend fun onReceive(subscriberId: String, block: suspend (Feedback) -> Unit): Job {

        val job = coroutineScope.launch {
            val redisConsumerClient = RedisConsumerClient(redisCommands, { value -> deserialize(value) }, idGenerator, subscriberId, consumerChannelName){
                block(it)
            }
            redisConsumerClient.start()

            redisConsumerClients.add(redisConsumerClient)
        }
        receivingJobs.add(job)

        return job
    }

    private fun deserialize(value: String): Feedback {
        return serializer.deserialize(value.toByteArray())
    }

    @PreDestroy
    fun close() {
        kotlin.runCatching {
            redisConsumerClients.forEach { it.stop() }
            receivingJobs.onEach { it.cancel() }
        }
    }

}
