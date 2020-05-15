package io.evolue.api.kotlin.components

interface AssertionDataSource<T> {

    suspend fun reset()

    suspend fun hasNext(): Boolean

    suspend fun next(): T
}