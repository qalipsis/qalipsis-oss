package io.qalipsis.api.steps.datasource

/**
 * Validates and/or transform an object read from a datasource.
 *
 * @param R type of the object to process
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectProcessor<R, O> {

    /**
     * Validates and/or transform an object and returns either the object itself or a different representation of it.
     */
    fun process(offset: Long, readObject: R): O

}
