package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Step in charge of processing the context once it is exhausted.
 *
 * This step is bypassed if the context is not exhausted.
 *
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStep<O>(
    id: StepId,
    private val block: (suspend (context: StepContext<*, O>) -> Unit)
) : AbstractStep<Any?, O>(id, null) {

    @ExperimentalCoroutinesApi
    override suspend fun execute(context: StepContext<Any?, O>) {
        if (context.isExhausted) {
            log.trace("Catching exhausted context")
            this.block(context)
        } else if (!context.input.isEmpty) {
            @Suppress("UNCHECKED_CAST")
            context.output.send(context.input.receive() as O)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
