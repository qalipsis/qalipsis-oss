package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeFactoryChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
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
internal class RedisHandshakeFactoryChannel(
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val serializer: DistributionSerializer,
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator,
    private val factoryConfiguration: FactoryConfiguration
) : HandshakeFactoryChannel {

    private val receivingJobs: MutableList<Job> = mutableListOf()

    private var redisConsumerClients: MutableList<RedisConsumerClient<HandshakeResponse>> = mutableListOf()

    @LogInputAndOutput
    override suspend fun send(request: HandshakeRequest) {
        redisCoroutinesCommands.xadd(factoryConfiguration.handshakeRequestChannel, mapOf(request.nodeId to serializer.serialize(request).decodeToString()))
    }

    override suspend fun onReceiveResponse(subscriberId: String, block: suspend (HandshakeResponse) -> Unit): Job {
        return coroutineScope.launch {
            val redisConsumerClient = RedisConsumerClient(redisCoroutinesCommands, { value -> deserialize(value) }, idGenerator, subscriberId, factoryConfiguration.handshakeResponseChannel) {
                if(it.handshakeNodeId == factoryConfiguration.nodeId){
                    block(it)
                }
            }
            redisConsumerClients.add(redisConsumerClient)
            redisConsumerClient.start()
        }.also(receivingJobs::add)
    }

    override suspend fun close() {
        closeChannels()
    }

    private fun deserialize(value: String): HandshakeResponse {
        return serializer.deserialize(value.toByteArray())
    }

    @PreDestroy
    fun closeChannels() {

        redisConsumerClients.forEach { kotlin.runCatching { it.stop() } }
        receivingJobs.onEach {
            kotlin.runCatching { it.cancel() }
        }
    }
}
