package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Step in charge of filter out all the record not matching the specification.
 *
 * @author Eric Jessé
 */
class FilterStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val specification: ((input: I) -> Boolean)
) : AbstractStep<I, I>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.input.receive()
        if (specification(input)) {
            log.trace("Forwarding the input")
            context.output.send(input)
        } else {
            log.trace("No data is forwarded")
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
