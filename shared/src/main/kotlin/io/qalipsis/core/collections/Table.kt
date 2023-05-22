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

/**
 * 2-Dimensional collection, accessible by row and column.
 *
 * Rows and columns have to consistently support [Any.equals] and [Any.hashCode].
 *
 * @author Eric Jessé
 */
interface Table<R, C, V> : Iterable<Pair<R, Map<C, V>>> {

    /**
     * Returns all the rows of the table.
     */
    val rows: Set<R>

    /**
     * Returns the number of items in the table.
     */
    val size: Int

    /**
     * Returns all the columns values for the specified [row].
     */
    fun columns(row: R): Set<C>

    /**
     * Counts the number of columns of the specified [row].
     */
    fun size(row: R): Int

    /**
     * Returns `true` if the table is empty (contains no elements), `false` otherwise.
     */
    fun isEmpty(): Boolean

    /**
     * Returns `true` if the row is empty (contains no columns), `false` otherwise.
     */
    fun isEmpty(row: R): Boolean

    /**
     * Returns `true` if the table contains at least one element, `false` otherwise.
     */
    fun isNotEmpty(): Boolean

    /**
     * Returns `true` if the row contains at least one element, `false` otherwise.
     */
    fun isNotEmpty(row: R): Boolean

    /**
     * Returns the value corresponding to the given [row] and [column], or `null` if such a key is not present in the table.
     */
    operator fun get(row: R, column: C): V?

    /**
     * Returns the [Map] of columns to values corresponding to the given [row], or `null` if such a row is not present in the table.
     */
    operator fun get(row: R): Map<C, V>?

    /**
     * Returns `true` if the table maps a value for the given [row] and [column].
     */
    fun exists(row: R, column: C): Boolean

    /**
     * Returns `true` if the table maps the given [row].
     */
    fun exists(row: R): Boolean

    /**
     * Creates a new table containing the content of this extended with the one of [other].
     */
    operator fun plus(other: Table<R, C, V>): Table<R, C, V> {
        val result = mutableTableOf(this)
        other.forEach { (row, columns) ->
            columns.forEach { (column, value) ->
                result.put(row, column, value)
            }
        }
        return result
    }

    /**
     * Creates a new table converting the row using the mapping function.
     */
    fun <R1> mapRows(mapping: (R, C, V) -> R1): Table<R1, C, V> {
        val result = mutableTableOf<R1, C, V>()
        this.forEach { (row, columns) ->
            columns.forEach { (column, value) ->
                result.put(mapping(row, column, value), column, value)
            }
        }
        return result
    }

    /**
     * Adds the [Triple] to the map.
     */
    operator fun plus(triple: Triple<R, C, V>): Table<R, C, V> {
        val result = mutableTableOf(this)
        val (row, column, value) = triple
        result.put(row, column, value)
        return result
    }
}

/**
 * Creates a new empty [MutableTable].
 */
fun <R, C, V> mutableTableOf(): MutableTable<R, C, V> = HashTable()

/**
 * Creates a new [MutableTable] initialized with [values].
 */
fun <R, C, V> mutableTableOf(values: Table<R, C, V>): MutableTable<R, C, V> = HashTable(values)

/**
 * Creates a new [MutableTable] initialized with [values].
 */
fun <R, C, V> mutableTableOf(values: Map<R, Map<C, V>>): MutableTable<R, C, V> = HashTable(values)

/**
 * Creates a new empty [Table].
 */
fun <R, C, V> tableOf(): Table<R, C, V> = mutableTableOf()

/**
 * Creates a new [Table] initialized with [values].
 */
fun <R, C, V> tableOf(values: Table<R, C, V>): Table<R, C, V> = mutableTableOf(values)

/**
 * Creates a new [Table] initialized with [values].
 */
fun <R, C, V> tableOf(values: Map<R, Map<C, V>>): Table<R, C, V> = mutableTableOf(values)

/**
 * Creates a new empty [ConcurrentTable].
 */
fun <R, C, V> concurrentTableOf(): ConcurrentTable<R, C, V> = ConcurrentHashTable()

/**
 * Creates a new [ConcurrentTable] initialized with [values].
 */
fun <R, C, V> concurrentTableOf(values: Table<R, C, V>): ConcurrentTable<R, C, V> = ConcurrentHashTable(values)

/**
 * Creates a new [ConcurrentTable] initialized with [values].
 */
fun <R, C, V> concurrentTableOf(values: Map<R, Map<C, V>>): ConcurrentTable<R, C, V> = ConcurrentHashTable(values)

/**
 * Returns a constant global empty table.
 */
@Suppress("UNCHECKED_CAST")
fun <R, C, V> emptyTable() = HashTable.EMPTY as Table<R, C, V>