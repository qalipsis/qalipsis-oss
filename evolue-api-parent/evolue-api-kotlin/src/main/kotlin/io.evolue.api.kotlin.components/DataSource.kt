package components

interface DataSource<T> {

    suspend fun reset()

    suspend fun hasNext(): Boolean

    suspend fun next(): T
}