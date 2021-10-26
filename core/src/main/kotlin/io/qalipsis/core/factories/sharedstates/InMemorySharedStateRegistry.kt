package io.qalipsis.core.factories.sharedstates

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.cross.configuration.ENV_STANDALONE
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of [SharedStateRegistry] based upon the cache library [Caffeine][https://github.com/ben-manes/caffeine].
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
class InMemorySharedStateRegistry(
    @Value("\${shared-registry.time-to-live:PT1M}") timeToLive: Duration
) : SharedStateRegistry {

    private val cache: Cache<SharedStateDefinition, Any?> = Caffeine.newBuilder()
        .expireAfter(SharedStateDefinitionExpiry(timeToLive))
        .build()

    override fun set(definition: SharedStateDefinition, payload: Any?) {
        if (payload == null) {
            cache.invalidate(definition)
        } else {
            cache.put(definition, payload)
        }
    }

    override fun set(values: Map<SharedStateDefinition, Any?>) {
        values.forEach { (def, value) -> set(def, value) }
    }

    override fun <T> get(definition: SharedStateDefinition): T? {
        @Suppress("UNCHECKED_CAST")
        return cache.getIfPresent(definition) as T
    }

    override fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return definitions.map { it.sharedStateName to get<Any>(it) }.toMap()
    }

    override fun <T> remove(definition: SharedStateDefinition): T? {
        return get<T>(definition)?.also { cache.invalidate(definition) }
    }

    override fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return get(definitions).also {
            definitions.forEach { cache.invalidate(it) }
        }
    }

    override fun contains(definition: SharedStateDefinition): Boolean {
        return cache.asMap().containsKey(definition)
    }

    private class SharedStateDefinitionExpiry(private val timeToLive: Duration) :
        Expiry<SharedStateDefinition, Any> {

        override fun expireAfterUpdate(key: SharedStateDefinition, value: Any, currentTime: Long,
            currentDuration: Long): Long {
            return key.timeToLive?.toNanos() ?: currentDuration
        }

        override fun expireAfterCreate(key: SharedStateDefinition, value: Any, currentTime: Long): Long {
            return key.timeToLive?.toNanos() ?: timeToLive.toNanos()
        }

        override fun expireAfterRead(key: SharedStateDefinition, value: Any, currentTime: Long,
            currentDuration: Long): Long {
            return key.timeToLive?.toNanos() ?: currentDuration
        }

    }
}
