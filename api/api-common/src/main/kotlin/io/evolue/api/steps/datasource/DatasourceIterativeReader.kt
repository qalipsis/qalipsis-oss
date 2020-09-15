package io.evolue.api.steps.datasource

/**
 * Reads objects from a datasource in an iterative way.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceIterativeReader<R> : Iterator<R> {

    fun start() = Unit

    fun stop() = Unit

}
