package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to transform a record.
 *
 * @author Eric Jessé
 */
internal class MapWithContextStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    @Suppress("UNCHECKED_CAST") private val block: ((context: StepContext<I, O>, input: I) -> O) = { _, value -> value as O }
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        val output = block(context, input)
        context.send(output)
    }

}
