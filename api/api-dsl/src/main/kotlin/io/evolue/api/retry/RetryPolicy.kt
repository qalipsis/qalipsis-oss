package io.evolue.api.retry

import io.evolue.api.context.StepContext

/**
 * Interface for rules to apply to execute a step and manage its failures and attempts.
 *
 * @author Eric Jessé
 */
interface RetryPolicy {

    suspend fun <I, O> execute(context: StepContext<I, O>, executable: suspend (StepContext<I, O>) -> Unit)
}