package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.steps.AbstractStep

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
