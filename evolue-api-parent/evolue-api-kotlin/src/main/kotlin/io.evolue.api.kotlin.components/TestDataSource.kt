package io.evolue.api.kotlin.components

interface TestDataSource<T> {

    suspend fun reset()

    suspend fun hasNext(): Boolean

    suspend fun next(): T
}