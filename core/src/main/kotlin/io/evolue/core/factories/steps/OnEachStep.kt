package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep

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
