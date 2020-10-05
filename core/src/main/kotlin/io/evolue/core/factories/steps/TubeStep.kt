package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.steps.AbstractStep

/**
 * Simple forwards the input to the output.
 *
 * @author Eric Jessé
 */
class TubeStep<I>(id: StepId) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        context.output.send(context.input.receive())
    }

}
