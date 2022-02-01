package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.AbstractStep

/**
 * Simple forwards the input to the output.
 *
 * @author Eric Jessé
 */
internal open class PipeStep<I>(id: StepId) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        context.send(context.receive())
    }

}
