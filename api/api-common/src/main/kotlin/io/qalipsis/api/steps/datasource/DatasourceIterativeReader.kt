package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepStartStopContext

/**
 * Reads objects from a datasource in an iterative way.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceIterativeReader<R> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Returns `true` if the iteration has more elements.
     */
    suspend operator fun hasNext(): Boolean

    /**
     * Returns the next element in the iteration.
     */
    suspend operator fun next(): R
}
