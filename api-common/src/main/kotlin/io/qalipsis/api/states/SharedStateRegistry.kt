package io.qalipsis.api.states

import io.qalipsis.api.context.MinionId

/**
 * Interface to share mutable states between steps. The implementations can be backed by in-memory or persistent caches.
 *
 * @author Eric Jessé
 */
interface SharedStateRegistry {

    suspend fun set(definition: SharedStateDefinition, payload: Any?)

    suspend fun set(values: Map<SharedStateDefinition, Any?>)

    suspend fun <T> get(definition: SharedStateDefinition): T?

    suspend fun <T> remove(definition: SharedStateDefinition): T?

    suspend fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun contains(definition: SharedStateDefinition): Boolean

    suspend fun clear()

    suspend fun clear(minionIds: Collection<MinionId>)

}
