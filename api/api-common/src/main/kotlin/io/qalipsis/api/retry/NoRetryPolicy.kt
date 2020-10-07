package io.qalipsis.api.retry

import io.qalipsis.api.context.StepContext

/**
 * Default no retry policy, there is no further attempt after a failure.
 *
 * @author Eric Jessé
 */
class NoRetryPolicy : RetryPolicy {

    override suspend fun <I, O> execute(context: StepContext<I, O>, executable: suspend (StepContext<I, O>) -> Unit) =
        executable(context)
}