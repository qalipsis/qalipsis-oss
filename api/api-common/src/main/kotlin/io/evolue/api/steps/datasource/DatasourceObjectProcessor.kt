package io.evolue.api.steps.datasource

/**
 * Validates and/or transform an object read from a datasource.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceObjectProcessor<R, O> {

    /**
     * Validates and/or transform an object and returns either the object itself or a different representation of it.
     */
    fun process(offset: Long, readObject: R): O

}
