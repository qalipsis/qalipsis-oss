package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY
import io.qalipsis.core.configuration.ExecutionEnvironments.REDIS
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.AbstractDirectiveConsumer
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.lifetime.FactoryStartupComponent
import io.qalipsis.core.redis.RedisConsumerClient
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.annotation.PreDestroy

/**
 * Implementation of [AbstractDirectiveConsumer] based upon [RedisCoroutinesCommands], used for deployments
 * other than [STANDALONE].
 *
 * @property redisCommands Redis Coroutines commands.
 * @param directiveProcessors Processors for the directives.
 * @property serializer Serializer for redis messages.
 * @property idGenerator Id generator.
 * @property coroutineScope Coroutine scope for execution of jobs.
 * @property factoryConfiguration Properties for configuration of the factory.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requirements(
    Requires(notEnv = [STANDALONE]),
    Requires(property = DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = REDIS)
)
@ExperimentalLettuceCoroutinesApi
internal class RedisBasedDirectiveConsumer(
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    directiveProcessors: Collection<DirectiveProcessor<*>>,
    private val serializer: DistributionSerializer,
    private val idGenerator: IdGenerator,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val factoryConfiguration: FactoryConfiguration
) : AbstractDirectiveConsumer(directiveProcessors), FactoryStartupComponent {

    private var unicastConsumerJob: Job? = null
    private var broadcastConsumerJob: Job? = null
    private var unicastRedisConsumerClient: RedisConsumerClient<Directive>? = null
    private var broadcastRedisConsumerClient: RedisConsumerClient<Directive>? = null
    private var running = false

    companion object {

        @JvmStatic
        private val log = logger()
    }

    override suspend fun start(unicastChannel: String, broadcastChannel: String) {
        if (!running) {
            running = true
            launchUnicastJob(unicastChannel)
            launchBroadcastJob(broadcastChannel)
        } else {
            throw IllegalStateException("Directives consumer was already called")
        }
    }

    private fun launchBroadcastJob(broadcastChannel: String) {
        broadcastConsumerJob = coroutineScope.launch {
            log.debug { "Consuming the directives from $broadcastChannel" }
            broadcastRedisConsumerClient =
                RedisConsumerClient(redisCommands, { value -> deserialize(value) }, idGenerator, factoryConfiguration.directiveRegistry.broadcastConsumerGroup, broadcastChannel) {
                    process(it)
                }
            broadcastRedisConsumerClient?.start()
        }
    }

    private fun launchUnicastJob(unicastChannel: String) {
        unicastConsumerJob = coroutineScope.launch {
            log.debug { "Consuming the directives from $unicastChannel" }
            unicastRedisConsumerClient = RedisConsumerClient(redisCommands, { value -> deserialize(value) }, idGenerator, factoryConfiguration.directiveRegistry.unicastConsumerGroup, unicastChannel) {
                process(it)
            }
            unicastRedisConsumerClient?.start()
        }
    }

    private fun deserialize(value: String): Directive {
        return serializer.deserialize(value.toByteArray())!!
    }

    @PreDestroy
    fun close() {
        kotlin.runCatching { unicastRedisConsumerClient?.stop() }
        kotlin.runCatching { broadcastRedisConsumerClient?.stop() }
        kotlin.runCatching { broadcastConsumerJob?.cancel() }
        kotlin.runCatching { unicastConsumerJob?.cancel() }
    }
}
