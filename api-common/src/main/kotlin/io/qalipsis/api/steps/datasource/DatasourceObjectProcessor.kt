package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepStartStopContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Validates and/or transform an object read from a datasource.
 *
 * @param R type of the object to process
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectProcessor<R, O> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Validates and/or transform an object and returns either the object itself or a different representation of it.
     *
     * @param offset an reference to the offset of the current value, it should not be changed by the implementation
     * @param readObject input directly received from the reader.
     */
    fun process(offset: AtomicLong, readObject: R): O

}
