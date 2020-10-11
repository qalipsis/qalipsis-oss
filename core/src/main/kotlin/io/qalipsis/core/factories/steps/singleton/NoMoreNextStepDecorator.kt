package io.qalipsis.core.factories.steps.singleton

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.Step

/**
 * Decorator for a step on which no more step step can be added.
 *
 * @author Eric Jessé
 */
class NoMoreNextStepDecorator<I, O>(
        internal val decorated: Step<I, O>
) : Step<I, O> {

    override val id: StepId = decorated.id

    override var retryPolicy = decorated.retryPolicy

    override val next: List<Step<O, *>>
        get() = decorated.next

    override fun addNext(nextStep: Step<*, *>) {
        // Do nothing.
    }

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun start(context: StepStartStopContext) {
        decorated.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        decorated.stop(context)
    }

    override suspend fun destroy() {
        decorated.destroy()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        decorated.execute(context)
    }
}
