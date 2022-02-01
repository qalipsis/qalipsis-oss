package io.qalipsis.core.collections

/**
 * Mutable [Table].
 *
 * @author Eric Jessé
 */
interface MutableTable<R, C, V> : Table<R, C, V> {

    /**
     * Removes all elements from this table.
     */
    fun clear()

    /**
     * Associates the specified [value] with the specified [row] and [column] in the table.
     */
    fun put(row: R, column: C, value: V)

    /**
     * Associates the specified [columns] with the specified [row] in the table,
     * removing all the existing columns of [row].
     */
    fun put(row: R, columns: Map<C, V>)

    /**
     * Associates the specified [columns] with the specified [row] in the table, extending the existing columns and
     * replacing all the columns with same key in the [row].
     */
    fun add(row: R, columns: Map<C, V>)

    /**
     * Extends this table with the content of [other].
     */
    fun putAll(other: Table<R, C, V>) {
        other.forEach { (row, columns) ->
            columns.forEach { (column, value) ->
                put(row, column, value)
            }
        }
    }

    /**
     * Exactly like [put], but usable as an operator.
     */
    operator fun set(row: R, column: C, value: V) = put(row, column, value)

    /**
     * Exactly like [put], but usable as an operator.
     */
    operator fun set(row: R, columns: Map<C, V>) = put(row, columns)

    /**
     * Extends this table with the content of [other].
     */
    operator fun plusAssign(other: Table<R, C, V>) {
        putAll(other)
    }

    /**
     * Removes the specified cell at [row] and [column] and its corresponding value from this table.
     *
     * @return the previous value associated with the [row] and [column], or `null` if the key was not present in the table.
     */
    fun remove(row: R, column: C): V?

    /**
     * Removes the specified [row] and its corresponding columns from this table.
     *
     * @return the previous columns and values associated with the [row], or `null` if the key was not present in the table.
     */
    fun remove(row: R): Map<C, V>?

    override fun plus(triple: Triple<R, C, V>): MutableTable<R, C, V> {
        val (row, column, value) = triple
        put(row, column, value)
        return this
    }
}