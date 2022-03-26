package io.qalipsis.runtime.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisListCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.directives.SingleUseDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton

/**
 * Implementation of [DirectiveRegistry] hosting the [Directive]s into Redis,
 * used for deployments other than [STANDALONE].
 *
 * @property idGenerator ID generator to create the directives keys.
 * @property serializer serializer for redis messages.
 * @property redisListCommands redis Coroutines commands.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(notEnv = [STANDALONE])
@ExperimentalLettuceCoroutinesApi
internal class RedisDirectiveRegistry(
    private val idGenerator: IdGenerator,
    private val serializer: DistributionSerializer,
    private val redisListCommands: RedisListCoroutinesCommands<String, String>
) : DirectiveRegistry {

    @LogInputAndOutput
    override suspend fun save(
        channel: DispatcherChannel,
        directive: SingleUseDirective<*>
    ): SingleUseDirectiveReference {
        val key = "${channel}:${idGenerator.long()}"
        val reference = directive.toReference(key)
        redisListCommands.lpush(key, serializer.serialize(directive).decodeToString())
        return reference
    }

    @LogInputAndOutput
    override suspend fun <T : SingleUseDirectiveReference> get(reference: T): Directive? {
        log.trace { "Reading single use directive with key ${reference.key}" }
        return redisListCommands.lpop(reference.key)?.let {
            log.trace { "Single use directive with key ${reference.key} was just retrieved (and removed)" }
            serializer.deserialize<SingleUseDirective<T>>(it.encodeToByteArray())
        }
    }

    private companion object {

        val log = logger()

    }

}