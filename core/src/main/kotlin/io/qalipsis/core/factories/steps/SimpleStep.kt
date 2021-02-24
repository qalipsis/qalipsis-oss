package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * The most simple kind of step, just processing the context as it comes, with a closure.
 *
 * @author Eric Jessé
 */
internal class SimpleStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val specification: (suspend (context: StepContext<I, O>) -> Unit)
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        specification(context)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
