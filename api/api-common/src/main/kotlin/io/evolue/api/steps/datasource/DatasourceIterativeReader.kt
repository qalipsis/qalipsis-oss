package io.evolue.api.steps.datasource

/**
 * Reads objects from a datasource in an iterative way.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceIterativeReader<R> {

    fun start() = Unit

    fun stop() = Unit

    /**
     * Returns `true` if the iteration has more elements.
     */
    suspend operator fun hasNext(): Boolean

    /**
     * Returns the next element in the iteration.
     */
    suspend operator fun next(): R
}
