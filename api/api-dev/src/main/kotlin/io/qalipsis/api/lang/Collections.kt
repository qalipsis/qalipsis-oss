package io.qalipsis.api.lang

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates a thread-safe [MutableSet].
 *
 * @author Eric Jessé
 */
fun <T> concurrentSet(): MutableSet<T> = Collections.newSetFromMap(ConcurrentHashMap())


/**
 * Creates a thread-safe [MutableSet] with the providing contents.
 *
 * @author Eric Jessé
 */
fun <T> concurrentSet(vararg values: T) = concurrentSet(values.toSet())

/**
 * Creates a thread-safe [MutableSet] with the providing contents.
 *
 * @author Eric Jessé
 */
fun <T> concurrentSet(values: Collection<T>): MutableSet<T> {
    val result = concurrentSet<T>()
    result.addAll(values)
    return result
}
