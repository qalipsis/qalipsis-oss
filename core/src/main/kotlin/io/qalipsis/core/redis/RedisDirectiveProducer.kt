package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.configuration.ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY
import io.qalipsis.core.configuration.ExecutionEnvironments.REDIS
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProducer
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.ReferencableDirective
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton
import org.slf4j.event.Level

/**
 * Implementation of [DirectiveProducer] publishing the [Directive]s via a [RedisCoroutinesCommands],
 * used for deployments other than [STANDALONE].
 *
 * @property redisCoroutinesCommands Redis Coroutines commands.
 * @property serializer Serializer for redis messages.
 * @property registry Directive registry.
 *
 * @author Gabriel Moraes
 */
@Singleton
@Requirements(
    Requires(notEnv = [STANDALONE]),
    Requires(property = DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = REDIS)
)
@ExperimentalLettuceCoroutinesApi
class RedisDirectiveProducer(
    private val registry: DirectiveRegistry,
    private val serializer: DistributionSerializer,
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>
) : DirectiveProducer {

    companion object {
        private val log = logger()
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun publish(directive: Directive) {
        when (directive) {
            is ReferencableDirective<*> -> {
                val ref = directive.toReference()
                registry.save(ref.key, directive)
                log.trace { "Sending directive into stream: ${ref.channel}" }
                redisCoroutinesCommands.xadd(ref.channel, mapOf(directive.key to serializer.serialize(ref).decodeToString()))

            }
            else -> {
                log.trace { "Sending directive into stream: ${directive.channel}" }
                redisCoroutinesCommands.xadd(directive.channel, mapOf(directive.key to serializer.serialize(directive).decodeToString()))
            }
        }
    }

}
