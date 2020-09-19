package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepError
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep

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
