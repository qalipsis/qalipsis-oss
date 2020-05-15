package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Simple step in charge of adding a delay in the processing chain.
 *
 * @author Eric Jessé
 */
class DelayedStepDecorator<I, O>(private val delay: Duration, private val decorated: Step<I, O>) : Step<I, O>,
    StepExecutor {

    override val id: StepId
        get() = decorated.id

    override val retryPolicy: RetryPolicy? = null

    override fun next(): MutableList<Step<O, *>> = decorated.next()

    override suspend fun execute(context: StepContext<I, O>) {
        delay(delay.toMillis())
        executeStep(decorated, context)
    }

    override suspend fun init() {
        // No-op.
    }

    override suspend fun destroy() {
        // No-op.
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}