package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton

/**
 * Implementation of [SharedStateRegistry] backed by Redis.
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
@Requires(notEnv = [STANDALONE])
@ExperimentalLettuceCoroutinesApi
internal class RedisSharedStateRegistry(
    private val redisCoroutinesCommands: RedisCoroutinesCommands<String, String>,
    private val serializer: DistributionSerializer,
    private val factoryConfiguration: FactoryConfiguration
) : SharedStateRegistry {

    private val ttl = factoryConfiguration.cache.ttl.toMillis()

    override suspend fun contains(definition: SharedStateDefinition): Boolean {
        return redisCoroutinesCommands.exists(buildKey(definition)) ?: 0L > 0L
    }

    override suspend fun <T> get(definition: SharedStateDefinition): T? {
        return redisCoroutinesCommands.get(buildKey(definition))?.encodeToByteArray()?.let(serializer::deserialize)
    }

    override suspend fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return definitions.associate { buildKey(it) to get<Any>(it) }
    }

    override suspend fun <T> remove(definition: SharedStateDefinition): T? {
        return get<T>(definition).also { redisCoroutinesCommands.unlink(buildKey(definition)) }
    }

    override suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return get(definitions).also {
            definitions.forEach { redisCoroutinesCommands.unlink(buildKey(it)) }
        }
    }

    override suspend fun set(definition: SharedStateDefinition, payload: Any?) {
        if (payload == null) {
            redisCoroutinesCommands.unlink(buildKey(definition))
        } else {
            redisCoroutinesCommands.psetex(
                buildKey(definition),
                ttl,
                serializer.serialize(payload).decodeToString()
            )
        }
    }

    override suspend fun set(values: Map<SharedStateDefinition, Any?>) {
        values.forEach { (def, value) -> set(def, value) }
    }

    fun buildKey(definition: SharedStateDefinition): String {
        return "${factoryConfiguration.cache.keyPrefix}:${definition.minionId}:${definition.sharedStateName}"
    }
}
