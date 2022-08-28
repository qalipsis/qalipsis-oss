/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
