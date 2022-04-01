package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Step executing a statement on each received value.
 *
 * @author Eric Jessé
 */
internal class OnEachStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val statement: (input: I) -> Unit
) : AbstractStep<I, I>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        statement(input)
        context.send(input)
    }

}
