package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.heartbeat.HeartbeatConsumer
import io.qalipsis.core.redis.RedisConsumerClient
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PreDestroy

/**
 * Redis implementation of [HeartbeatConsumer].
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE]),
    Requires(property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
)
@ExperimentalLettuceCoroutinesApi
internal class RedisHeartbeatConsumerChannel(
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val serializer: DistributionSerializer,
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val idGenerator: IdGenerator,
    private val headConfiguration: HeadConfiguration
) : HeartbeatConsumer {

    private lateinit var redisConsumerClient: RedisConsumerClient<Heartbeat>

    override suspend fun start(channelName: String, onReceive: suspend (Heartbeat) -> Unit): Job {
        return coroutineScope.launch {
            val redisConsumerClient = RedisConsumerClient(redisCommands, { value -> deserialize(value) }, idGenerator, headConfiguration.heartbeatConsumerGroupName, channelName) {
                onReceive(it)
            }
            redisConsumerClient.start()
        }
    }

    private fun deserialize(value: String): Heartbeat {
        return serializer.deserialize(value.toByteArray())
    }

    @PreDestroy
    fun closeChannels() {

        kotlin.runCatching {
            redisConsumerClient.stop()
        }
    }
}
