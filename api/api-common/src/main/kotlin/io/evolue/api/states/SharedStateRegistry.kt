package io.evolue.api.states

/**
 * Interface to share mutable states between steps. The implementations can be backed by in-memory or persistent caches.
 *
 * @author Eric Jessé
 */
interface SharedStateRegistry {

    operator fun set(definition: SharedStateDefinition, payload: Any)

    operator fun <T> get(definition: SharedStateDefinition): T?

    fun contains(definition: SharedStateDefinition): Boolean

}