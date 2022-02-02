package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeHeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.redis.RedisConsumerClient
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PreDestroy

/**
 * Implementation of [HandshakeHeadChannel] used for deployments using [REDIS] for streaming platform.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE]),
    Requires(property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
)
@ExperimentalLettuceCoroutinesApi
internal class RedisHandshakeHeadChannel(
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val serializer: DistributionSerializer,
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator,
    private val headConfiguration: HeadConfiguration
) : HandshakeHeadChannel {
    private val receivingJobs: MutableList<Job> = mutableListOf()
    private var redisConsumerClients: MutableList<RedisConsumerClient<HandshakeRequest>> = mutableListOf()

    @LogInputAndOutput
    override suspend fun onReceiveRequest(subscriberId: String, block: suspend (HandshakeRequest) -> Unit): Job {
        return coroutineScope.launch {
            val redisConsumerClient = RedisConsumerClient(redisCoroutinesCommands, { value -> deserialize(value) }, idGenerator, subscriberId, headConfiguration.handshakeRequestChannel) {
                block(it)
            }
            redisConsumerClients.add(redisConsumerClient)
            redisConsumerClient.start()
        }.also(receivingJobs::add)
    }

    private fun deserialize(value: String): HandshakeRequest {
        return serializer.deserialize(value.toByteArray())
    }

    @LogInputAndOutput
    override suspend fun sendResponse(channelName: String, response: HandshakeResponse) {
        redisCoroutinesCommands.xadd(channelName, mapOf(response.handshakeNodeId to serializer.serialize(response).decodeToString()))
    }

    @PreDestroy
    fun closeChannels() {
        redisConsumerClients.forEach {
            kotlin.runCatching {
                it.stop()
            }
        }
        receivingJobs.onEach {
            kotlin.runCatching {
                it.cancel()
            }
        }
    }
}
