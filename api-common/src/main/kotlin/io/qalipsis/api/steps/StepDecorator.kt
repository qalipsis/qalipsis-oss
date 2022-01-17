package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.runtime.Minion

/**
 * Interface for any implementation of step decorator.
 *
 * @author Eric Jessé
 */
interface StepDecorator<I, O> : Step<I, O> {

    val decorated: Step<I, O>

    override fun addNext(nextStep: Step<*, *>) {
        decorated.addNext(nextStep)
    }

    override suspend fun init() {
        decorated.init()
        super.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
        super.destroy()
    }

    override suspend fun start(context: StepStartStopContext) {
        decorated.start(context)
        super.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        decorated.stop(context)
        super.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        decorated.execute(minion, context)
    }

    override suspend fun execute(context: StepContext<I, O>) {
        decorated.execute(context)
    }
}
