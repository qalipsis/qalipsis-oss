package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Step executing a statement on each received value.
 *
 * @author Eric Jessé
 */
class OnEachStep<I>(
        id: StepId,
        retryPolicy: RetryPolicy?,
        private val statement: (input: I) -> Unit
) : AbstractStep<I, I>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.input.receive()
        statement(input)
        context.output.send(input)
    }

}
