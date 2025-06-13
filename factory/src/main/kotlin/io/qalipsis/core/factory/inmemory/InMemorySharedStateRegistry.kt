/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.inmemory

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of [SharedStateRegistry] based upon the cache library [Caffeine][https://github.com/ben-manes/caffeine].
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY])
internal class InMemorySharedStateRegistry(
    @Value("\${factory.cache.ttl:PT1M}") timeToLive: Duration
) : SharedStateRegistry {

    private val cache: Cache<SharedStateDefinition, Any?> = Caffeine.newBuilder()
        .expireAfter(SharedStateDefinitionExpiry(timeToLive))
        .build()

    override suspend fun set(definition: SharedStateDefinition, payload: Any?) {
        if (payload == null) {
            cache.invalidate(definition)
        } else {
            cache.put(definition, payload)
        }
    }

    override suspend fun set(values: Map<SharedStateDefinition, Any?>) {
        values.forEach { (def, value) -> set(def, value) }
    }

    override suspend fun <T> get(definition: SharedStateDefinition): T? {
        @Suppress("UNCHECKED_CAST")
        return cache.getIfPresent(definition) as T
    }

    override suspend fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return definitions.associate { it.sharedStateName to get<Any>(it) }
    }

    override suspend fun <T> remove(definition: SharedStateDefinition): T? {
        return get<T>(definition)?.also { cache.invalidate(definition) }
    }

    override suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?> {
        return get(definitions).also {
            definitions.forEach { cache.invalidate(it) }
        }
    }

    override suspend fun contains(definition: SharedStateDefinition): Boolean {
        return cache.asMap().containsKey(definition)
    }

    override suspend fun clear() {
        cache.invalidateAll()
    }

    override suspend fun clear(minionIds: Collection<MinionId>) {
        cache.invalidateAll(cache.asMap().keys.filter { it.minionId in minionIds })
    }

    private class SharedStateDefinitionExpiry(private val timeToLive: Duration) :
        Expiry<SharedStateDefinition, Any> {

        override fun expireAfterUpdate(
            key: SharedStateDefinition, value: Any, currentTime: Long,
            currentDuration: Long
        ): Long {
            return key.timeToLive?.toNanos() ?: currentDuration
        }

        override fun expireAfterCreate(key: SharedStateDefinition, value: Any, currentTime: Long): Long {
            return key.timeToLive?.toNanos() ?: timeToLive.toNanos()
        }

        override fun expireAfterRead(
            key: SharedStateDefinition, value: Any, currentTime: Long,
            currentDuration: Long
        ): Long {
            return key.timeToLive?.toNanos() ?: currentDuration
        }

    }

    @PreDestroy
    fun close() {
        kotlin.runCatching {
            cache.invalidateAll()
            cache.cleanUp()
        }
    }
}