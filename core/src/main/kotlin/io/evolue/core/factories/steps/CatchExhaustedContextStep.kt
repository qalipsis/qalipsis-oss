package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStep

/**
 * Step in charge of processing the context once it is exhausted.
 *
 * This step is bypassed if the context is not exhausted.
 *
 * @author Eric Jessé
 */
class CatchExhaustedContextStep<I, O>(
    id: StepId,
    private val block: (suspend (context: StepContext<I, O>) -> Unit)
) : AbstractStep<I, O>(id, null) {

    override suspend fun execute(context: StepContext<I, O>) {
        if (context.isExhausted) {
            log.trace("Catching exhausted context")
            this.block(context)
        } else {
            log.trace("No context is not exhausted, the step is bypassed")
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
