package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.factory.context.StepContextImpl

/**
 * Step to consume data without producing anything.
 *
 * @author Eric Jessé
 */
internal open class BlackHoleStep<I>(
    id: StepName
) : AbstractStep<I, Unit>(id, null) {

    override suspend fun execute(context: StepContext<I, Unit>) {
        if (context is StepContextImpl) {
            context.output.close()
        }
        context.receive()
    }
}
