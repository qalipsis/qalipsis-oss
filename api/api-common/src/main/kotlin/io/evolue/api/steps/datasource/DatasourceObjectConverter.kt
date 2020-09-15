package io.evolue.api.steps.datasource

/**
 * Converts a value read from a datasource into a data that can be forwarded to next steps.
 *
 * @author Eric Jessé
 */
interface DatasourceObjectConverter<R, O> {

    fun supply(offset: Long, value: R): O

}