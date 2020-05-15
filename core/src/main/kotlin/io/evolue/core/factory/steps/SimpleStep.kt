package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep

/**
 * The most simple kind of step, just processing the context as it comes, with a closure.
 *
 * @author Eric Jessé
 */
class SimpleStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val specification: (suspend (context: StepContext<I, O>) -> Unit)
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        specification(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}