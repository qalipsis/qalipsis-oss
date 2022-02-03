package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.runtime.Minion

/**
 * Default interface for the components executing a step either directly or with its retry policy.
 *
 * @author Eric Jessé
 */
interface StepExecutor {

    /**
     * Executes a step either using its retry policy if defined or directly otherwise.
     */
    suspend fun <I, O> executeStep(minion: Minion, step: Step<I, O>, stepContext: StepContext<I, O>) {
        if (step.retryPolicy != null) {
            step.retryPolicy!!.execute(stepContext) { step.execute(minion, it) }
        } else {
            step.execute(minion, stepContext)
        }
    }

}
