package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.ErrorProcessingStep

/**
 * Step in charge of processing the errors received from the ancestors.
 *
 * This step is bypassed if there are no errors.
 *
 * @author Eric Jessé
 */
class CatchErrorStep<I>(
    id: StepId,
    private val block: ((error: Collection<StepError>) -> Unit)
) : AbstractStep<I, I>(id, null), ErrorProcessingStep<I, I> {

    @Throws(Throwable::class)
    override suspend fun execute(context: StepContext<I, I>) {
        if (context.errors.isNotEmpty()) {
            log.trace("${context.errors.size} error(s) to be caught")
            this.block(context.errors)
        } else {
            log.trace("No error to be caught, the step is bypassed")
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
