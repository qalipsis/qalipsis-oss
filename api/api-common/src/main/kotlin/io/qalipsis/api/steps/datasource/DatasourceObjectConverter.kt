package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepOutput
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts a value read from a datasource into a data that can be forwarded to next steps and sends it to the output channel.
 *
 * @param R type of the object to convert
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectConverter<R, O> {

    /**
     * Sends [value] to the [output] channel in any form.
     *
     * @param offset an reference to the offset, it is up to the implementation to increment it
     * @param value input value to send after any conversion to the output
     * @param output channel to received the data after conversion
     */
    suspend fun supply(offset: AtomicLong, value: R, output: StepOutput<O>)

}
