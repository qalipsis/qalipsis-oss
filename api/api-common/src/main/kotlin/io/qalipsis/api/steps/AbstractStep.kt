package io.qalipsis.api.steps

import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.RetryPolicy

/**
 * Simple super class of steps in order to perform generic operations without redundancy.
 *
 * @author Eric Jessé
 */
abstract class AbstractStep<I, O>(override val id: StepId, override var retryPolicy: RetryPolicy?) : Step<I, O> {

    override val next: MutableList<Step<O, *>> = mutableListOf()

    override fun addNext(nextStep: Step<*, *>) {
        @Suppress("UNCHECKED_CAST")
        next.add(nextStep as Step<O, *>)
    }
}
