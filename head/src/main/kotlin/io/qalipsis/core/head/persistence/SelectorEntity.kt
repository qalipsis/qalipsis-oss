package io.qalipsis.core.head.persistence

/**
 * General interface for selectors entities.
 *
 * @author Eric Jessé
 */
internal interface SelectorEntity<T : SelectorEntity<T>> {

    val key: String

    val value: String

    fun withValue(value: String): T
}