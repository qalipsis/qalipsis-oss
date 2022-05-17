package io.qalipsis.core.head.jdbc

/**
 * General interface for tags entities.
 *
 * @author Eric Jessé
 */
internal interface SelectorEntity<T : SelectorEntity<T>> {

    val key: String

    val value: String

    fun withValue(value: String): T
}