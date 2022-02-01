package io.qalipsis.core.collections

/**
 * Naive implementation of a simple table.
 */
internal open class HashTable<R, C, V>() : MutableTable<R, C, V> {

    constructor(values: Table<R, C, V>) : this() {
        values.forEach { (row, columns) ->
            delegate[row] = columns.toMutableMap()
        }
    }

    constructor(values: Map<R, Map<C, V>>) : this() {
        values.forEach { (key, maps) ->
            delegate[key] = maps.toMutableMap()
        }
    }

    protected open val delegate = mutableMapOf<R, MutableMap<C, V>>()

    override val rows
        get() = delegate.keys

    override val size
        get() = delegate.size

    override fun columns(row: R) = delegate[row]?.keys ?: emptySet()

    override fun size(row: R) = delegate[row]?.size ?: 0

    override fun isEmpty() = delegate.isEmpty()

    override fun isEmpty(row: R) = delegate[row]?.isEmpty() ?: true

    override fun isNotEmpty() = delegate.isNotEmpty()

    override fun isNotEmpty(row: R) = delegate[row]?.isNotEmpty() ?: false

    override fun clear() = delegate.clear()

    override fun put(row: R, column: C, value: V) {
        delegate.computeIfAbsent(row) { mutableMapOf() }[column] = value
    }

    override operator fun get(row: R, column: C): V? {
        return delegate[row]?.get(column)
    }

    override operator fun get(row: R): Map<C, V>? {
        return delegate[row]
    }

    override fun exists(row: R, column: C): Boolean {
        return delegate[row]?.keys?.contains(column) ?: false
    }

    override fun exists(row: R): Boolean {
        return delegate.keys.contains(row)
    }

    override fun remove(row: R, column: C): V? {
        val value = delegate[row]?.remove(column)
        if (delegate[row]!!.isEmpty()) {
            delegate.remove(row)
        }
        return value
    }

    override fun remove(row: R): Map<C, V>? {
        return delegate.remove(row)
    }

    override fun put(row: R, columns: Map<C, V>) {
        delegate[row] = columns.toMutableMap()
    }

    override fun add(row: R, columns: Map<C, V>) {
        columns.forEach { (column, value) ->
            put(row, column, value)
        }
    }

    override fun iterator(): Iterator<Pair<R, Map<C, V>>> {
        return TableIterator(delegate.iterator())
    }

    companion object {

        @JvmStatic
        val EMPTY = HashTable<Any?, Any?, Any?>()
    }

    private class TableIterator<R, C, V>(private val delegate: Iterator<Map.Entry<R, Map<C, V>>>) :
        Iterator<Pair<R, Map<C, V>>> {

        override fun hasNext(): Boolean = delegate.hasNext()

        override fun next(): Pair<R, Map<C, V>> {
            return delegate.next().toPair()
        }

    }

}