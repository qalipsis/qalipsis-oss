/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

/**
 * Polls the first [count] items in a new [List] in the limit of this size. If [count] is 0, a new empty [List] is returned.
 */
fun <T> MutableList<T>.pollFirst(count: Int): List<T> {
    val result = mutableListOf<T>()
    repeat(count.coerceAtMost(this.size)) {
        result.add(this.removeAt(0))
    }
    return result
}
