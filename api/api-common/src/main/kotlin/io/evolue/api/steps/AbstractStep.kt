package io.evolue.api.steps

import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy

/**
 * Simple super class of steps in order to perform generic operations without redundancy.
 *
 * @author Eric Jessé
 */
abstract class AbstractStep<I, O>(override val id: StepId, override val retryPolicy: RetryPolicy?) : Step<I, O> {

    private val nextSteps: MutableList<Step<O, *>> = mutableListOf()

    override fun next() = nextSteps

    override suspend fun init() {
        // No-op.
    }

    override suspend fun destroy() {
        // No-op.
    }
}