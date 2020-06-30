package io.evolue.core.factory.sharedstates

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.evolue.api.states.SharedStateDefinition
import io.evolue.api.states.SharedStateRegistry
import io.evolue.core.cross.configuration.ENV_STANDALONE
import io.micronaut.context.annotation.Requires
import java.time.Duration
import javax.inject.Singleton

/**
 * Implementation of [SharedStateRegistry] based upon the cache library [Caffeine][https://github.com/ben-manes/caffeine].
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_STANDALONE])
class InMemorySharedStateRegistry(timeToLive: Duration) : SharedStateRegistry {

    private val cache: Cache<SharedStateDefinition, Any> = Caffeine.newBuilder()
        .expireAfter(SharedStateDefinitionExpiry(timeToLive))
        .build()

    override fun set(definition: SharedStateDefinition, payload: Any) {
        cache.put(definition, payload)
    }

    override fun <T> get(definition: SharedStateDefinition): T? {
        return cache.getIfPresent(definition) as T
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
