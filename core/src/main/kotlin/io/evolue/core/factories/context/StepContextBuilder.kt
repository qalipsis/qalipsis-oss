package io.evolue.core.factories.context

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId

/**
 * Helper class to build step contexts.
 *
 * @author Eric Jessé
 */
internal object StepContextBuilder {

    /**
     * Create context for next step with an input value.
     */
    fun <A : Any?, I : Any?, O : Any?> next(input: I, ctx: StepContext<A, I>,
        stepId: StepId): StepContext<I, O> {
        return ctx.next(input, stepId)
    }

    /**
     * Create context for next step without input value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <I : Any?, O : Any?> next(ctx: StepContext<I, O>, stepId: StepId): StepContext<Unit, O> {
        return ctx.next<O>(stepId) as StepContext<Unit, O>
    }
}
