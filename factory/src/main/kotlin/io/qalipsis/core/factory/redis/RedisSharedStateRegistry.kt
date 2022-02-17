package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

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

    private val keysByMinions = ConcurrentHashMap<MinionId, MutableSet<String>>()

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
        removeKeyByMinionId(definition)
        val key = buildKey(definition)
        return removeKey<T>(key)
    }

    private suspend fun <T> removeKey(key: String): T? = redisCoroutinesCommands.eval<String>(
        LUA_SINGULAR_REMOVAL_SCRIPT,
        ScriptOutputType.VALUE,
        key
    )?.encodeToByteArray()?.let(serializer::deserialize)

    override suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        definitions.forEach { removeKeyByMinionId(it) }
        return definitions.associateBy(this::buildKey).mapValues { removeKey(it.key) }
    }

    override suspend fun set(definition: SharedStateDefinition, payload: Any?) {
        if (payload == null) {
            redisCoroutinesCommands.unlink(buildKey(definition))
            removeKeyByMinionId(definition)
        } else {
            redisCoroutinesCommands.psetex(
                buildKey(definition),
                ttl,
                serializer.serialize(payload).decodeToString()
            )
            addKeyByMinionId(definition)
        }
    }

    fun addKeyByMinionId(definition: SharedStateDefinition) {
        keysByMinions.computeIfAbsent(definition.minionId) { concurrentSet() }.add(buildKey(definition))
    }

    fun removeKeyByMinionId(definition: SharedStateDefinition) {
        keysByMinions.computeIfPresent(definition.minionId) {
            _, value -> value.remove(buildKey(definition))
            value.takeUnless { it.isEmpty() }
        }
    }

    override suspend fun set(values: Map<SharedStateDefinition, Any?>) {
        values.forEach { (def, value) -> set(def, value) }
    }

    override suspend fun clear() {
        keysByMinions.values.flatten().forEach { removeKey<String>(it) }
        keysByMinions.clear()
    }

    override suspend fun clear(minionIds: Collection<MinionId>) {
        minionIds.flatMap { keysByMinions[it].orEmpty() }.forEach { removeKey<String>(it) }
        minionIds.forEach { keysByMinions.remove(it) }
    }

    fun buildKey(definition: SharedStateDefinition): String {
        return "${factoryConfiguration.cache.keyPrefix}:${definition.minionId}:${definition.sharedStateName}"
    }

    private companion object {

        /**
         * LUA script to get and remove a key from Redis in a single call to avoid concurrency race between get and unlink.
         */
        const val LUA_SINGULAR_REMOVAL_SCRIPT = """
local result = redis.call('get', KEYS[1])
redis.call('unlink', KEYS[1])
return result
        """
    }
}
