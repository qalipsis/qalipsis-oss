package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Simple step in charge of adding a delay in the processing chain.
 *
 * @author Eric Jessé
 */
internal class DelayStep<I>(
    id: StepId,
    private val delay: Duration
) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        delay(delay.toMillis())
        context.send(context.receive())
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
