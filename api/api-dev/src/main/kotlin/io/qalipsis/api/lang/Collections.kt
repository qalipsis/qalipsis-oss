package io.qalipsis.api.lang

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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


/**
 * Creates a thread-safe [MutableList].
 *
 * @author Eric Jessé
 */
fun <T> concurrentList(): MutableList<T> = CopyOnWriteArrayList()


/**
 * Creates a thread-safe [MutableList] with the providing contents.
 *
 * @author Eric Jessé
 */
fun <T> concurrentList(vararg values: T) = concurrentList(values.toList())

/**
 * Creates a thread-safe [MutableList] with the providing contents.
 *
 * @author Eric Jessé
 */
fun <T> concurrentList(values: Collection<T>): MutableList<T> {
    val result = concurrentList<T>()
    result.addAll(values)
    return result
}
