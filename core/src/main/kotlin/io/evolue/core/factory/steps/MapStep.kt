package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep

/**
 * Step to transform a record.
 *
 * @author Eric Jessé
 */
class MapStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val block: ((input: I) -> O) = { value -> value as O }
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.input.receive()
        val output = block(input)
        context.output.send(output)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}