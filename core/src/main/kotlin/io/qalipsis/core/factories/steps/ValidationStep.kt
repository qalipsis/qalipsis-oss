package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 *
 * Step in charge of generating errors for each record not matching the specification.
 *
 * The step forward the input to the output to let the processing going on. It is generally associated
 * to a {@link CatchErrorStep} in order to analyze the errors and decide what to do with the record.
 *
 * @author Eric Jessé
 */
class ValidationStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val specification: ((input: I) -> List<StepError>)
) : AbstractStep<I, I>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.input.receive()
        val errors = specification(input)
        context.errors.addAll(errors)
        context.output.send(input)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
