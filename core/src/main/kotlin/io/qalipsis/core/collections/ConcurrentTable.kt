package io.qalipsis.core.collections

/**
 * Mutable [Table] designed to support concurrency.
 *
 * @author Eric Jessé
 */
interface ConcurrentTable<R, C, V> : MutableTable<R, C, V> {

    /**
     * Returns the value corresponding to the given [row] and [column], or uses the [supplier] to create the value
     * if it does not exist.
     *
     * The access to [row] and [column] are synchronized among all the caller.
     */
    fun computeIfAbsent(row: R, column: C, supplier: (C) -> V): V

}