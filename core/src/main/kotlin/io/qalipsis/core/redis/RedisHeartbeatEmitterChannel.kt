package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.heartbeat.HeartbeatEmitter
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.annotation.PreDestroy

/**
 * Redis implementation of [HeartbeatEmitter].
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requirements(
    Requires(notEnv = [ExecutionEnvironments.STANDALONE]),
    Requires(property = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
)
@ExperimentalLettuceCoroutinesApi
internal class RedisHeartbeatEmitterChannel(
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val serializer: DistributionSerializer,
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>
) : HeartbeatEmitter {

    companion object {
        private val log = logger()
    }

    private var heartbeatJob: Job? = null

    private lateinit var factoryNodeId: String

    private lateinit var channelName: String

    override suspend fun start(factoryNodeId: String, channelName: String, period: Duration) {
        this.factoryNodeId = factoryNodeId
        this.channelName = channelName
        heartbeatJob = coroutineScope.launch {
            try {
                sendHeartbeat(channelName, factoryNodeId, period)
            } catch (ex: Exception) {
                when (ex) {
                    is InterruptedException, is CancellationException -> {
                        log.trace { "Job was interrupted" }
                    }
                    else -> throw ex
                }
            }
        }
    }

    private suspend fun sendHeartbeat(channelName: String, factoryNodeId: String, period: Duration) {
        while (true) {
            kotlin.runCatching {
                redisCoroutinesCommands.xadd(channelName, mapOf(factoryNodeId to serializer.serialize(Heartbeat(factoryNodeId, Instant.now())).decodeToString()))
            }
            delay(period.toMillis())
        }
    }

    @PreDestroy
    suspend fun closeChannels() {
        sendUnregisteredHeartbeat(channelName, factoryNodeId)
        heartbeatJob?.cancel()
    }

    private suspend fun sendUnregisteredHeartbeat(channelName: String, factoryNodeId: String) {
        heartbeatJob.runCatching {
            redisCoroutinesCommands.xadd(
                channelName,
                mapOf(factoryNodeId to serializer.serialize(Heartbeat(factoryNodeId, Instant.now(), Heartbeat.STATE.UNREGISTERED)).decodeToString())
            )
        }
    }
}
