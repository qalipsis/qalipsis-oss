package io.evolue.api.states

/**
 * Interface to share mutable states between steps. The implementations can be backed by in-memory or persistent caches.
 *
 * @author Eric Jessé
 */
interface SharedStateRegistry {

    operator fun set(definition: SharedStateDefinition, payload: Any?)

    fun set(values: Map<SharedStateDefinition, Any?>)

    operator fun <T> get(definition: SharedStateDefinition): T?

    fun <T> remove(definition: SharedStateDefinition): T?

    fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    fun contains(definition: SharedStateDefinition): Boolean

}
