package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStep
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Simple step in charge of adding a delay in the processing chain.
 *
 * @author Eric Jessé
 */
class DelayStep<I>(
    id: StepId,
    private val delay: Duration
) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        delay(delay.toMillis())
        context.output.send(context.input.receive())
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}