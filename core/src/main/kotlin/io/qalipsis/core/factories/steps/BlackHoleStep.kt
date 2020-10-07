package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to consume data without producing anything.
 *
 * @author Eric Jessé
 */
class BlackHoleStep<I, O>(
    id: StepId
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        context.output.close()
        context.input.receive()
    }
}
