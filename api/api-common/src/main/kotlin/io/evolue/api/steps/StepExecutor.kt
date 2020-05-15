package io.evolue.api.steps

import io.evolue.api.context.StepContext

/**
 * Default interface for the components executing a step either directly or with its retry policy.
 *
 * @author Eric Jessé
 */
interface StepExecutor {

    /**
     * Execute a step either using its retry policy if defined or directly otherwise.
     */
    suspend fun <I, O> executeStep(step: Step<I, O>, context: StepContext<I, O>) {
        if (step.retryPolicy != null) {
            step.retryPolicy!!.execute(context) { stepContext ->
                step.execute(stepContext)
            }
        } else {
            step.execute(context)
        }
    }

}