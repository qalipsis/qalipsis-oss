package io.qalipsis.core.factory.context

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId

/**
 * Helper class to build step contexts.
 *
 * @author Eric Jessé
 */
internal object StepContextBuilder {

    /**
     * Creates a context for next step when an input value is to be provided.
     */
    fun <A, I, O> next(input: I, ctx: StepContext<A, I>, stepId: StepId): StepContext<I, O> {
        return ctx.next(input, stepId)
    }

    /**
     * Creates a context for next step without input value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <I, O> next(ctx: StepContext<I, O>, stepId: StepId): StepContext<Unit, O> {
        return ctx.next<O>(stepId) as StepContext<Unit, O>
    }
}
