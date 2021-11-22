package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.context.StepContextImpl

/**
 * Step to consume data without producing anything.
 *
 * @author Eric Jessé
 */
internal class BlackHoleStep<I, O>(
    id: StepId
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        if (context is StepContextImpl) {
            context.output.close()
        }
        context.receive()
    }
}
