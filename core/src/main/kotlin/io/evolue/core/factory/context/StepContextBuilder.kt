package io.evolue.core.factory.context

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import kotlinx.coroutines.channels.Channel

/**
 * Helper class to build step contexts.
 *
 * @author Eric Jessé
 */
internal object StepContextBuilder {

    suspend fun <A : Any?, I : Any?, O : Any?> next(input: I, ctx: StepContext<A, I>,
                                                    stepId: StepId): StepContext<I, O> {
        val inputChannel = Channel<I>(1)
        inputChannel.send(input)
        return StepContext(
            input = inputChannel,
            errors = ctx.errors,
            minionId = ctx.minionId,
            scenarioId = ctx.scenarioId,
            directedAcyclicGraphId = ctx.directedAcyclicGraphId,
            parentStepId = ctx.stepId,
            stepId = stepId,
            exhausted = ctx.exhausted,
            completed = ctx.completed,
            creation = ctx.creation
        )
    }

    fun <I : Any?, O : Any?> next(ctx: StepContext<I, O>, stepId: StepId): StepContext<Unit, O> {
        return StepContext(
            input = Channel<Unit>(1),
            errors = ctx.errors,
            minionId = ctx.minionId,
            scenarioId = ctx.scenarioId,
            directedAcyclicGraphId = ctx.directedAcyclicGraphId,
            parentStepId = ctx.stepId,
            stepId = stepId,
            exhausted = ctx.exhausted,
            completed = ctx.completed,
            creation = ctx.creation
        )
    }
}