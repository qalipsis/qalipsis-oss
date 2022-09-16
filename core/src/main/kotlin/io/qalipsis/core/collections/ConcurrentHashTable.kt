/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.collections

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Very simple equivalent of the Guava table for concurrency purpose.
 *
 * @param R type of the rows
 * @param C type of the columns
 * @param V type of the value
 *
 * @author Eric Jessé
 */
internal class ConcurrentHashTable<R, C, V>() : HashTable<R, C, V>(), ConcurrentTable<R, C, V> {

    @Suppress("UNCHECKED_CAST")
    override fun remove(row: R, column: C): V? {
        val value = AtomicReference<V?>(null)
        (delegate as ConcurrentHashMap<R, ConcurrentHashMap<C, V>>)
            .computeIfPresent(row) { _, columns ->
                value.set(columns.remove(column))
                columns.ifEmpty { null }
            }
        return value.get()
    }

    constructor(values: Map<R, Map<C, V>>) : this() {
        values.forEach { (key, maps) ->
            delegate[key] = ConcurrentHashMap(maps)
        }
    }

    constructor(values: Table<R, C, V>) : this() {
        values.forEach { (row, columns) ->
            delegate[row] = ConcurrentHashMap(columns)
        }
    }

    override val delegate = ConcurrentHashMap<R, MutableMap<C, V>>()

    override fun computeIfAbsent(row: R, column: C, supplier: (C) -> V): V {
        return delegate.computeIfAbsent(row) { ConcurrentHashMap() }.computeIfAbsent(column, supplier)
    }

    override fun put(row: R, column: C, value: V) {
        delegate.computeIfAbsent(row) { ConcurrentHashMap() }[column] = value
    }

    override fun put(row: R, columns: Map<C, V>) {
        delegate[row] = ConcurrentHashMap(columns)
    }
}