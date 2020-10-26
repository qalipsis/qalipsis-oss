package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep

/**
 * Step in charge of processing the context once it is exhausted.
 *
 * This step is bypassed if the context is not exhausted.
 *
 * @author Eric Jessé
 */
class CatchExhaustedContextStep<O>(
    id: StepId,
    private val block: (suspend (context: StepContext<*, O>) -> Unit)
) : AbstractStep<Any?, O>(id, null) {

    override suspend fun execute(context: StepContext<Any?, O>) {
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
