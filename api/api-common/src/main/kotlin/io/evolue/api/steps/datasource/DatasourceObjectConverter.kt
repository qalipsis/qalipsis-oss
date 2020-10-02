package io.evolue.api.steps.datasource

import kotlinx.coroutines.channels.SendChannel

/**
 * Converts a value read from a datasource into a data that can be forwarded to next steps and sends it to the output channel.
 *
 * @param R type of the object to convert
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectConverter<R, O> {

    suspend fun supply(offset: Long, value: R, output: SendChannel<O>)

}