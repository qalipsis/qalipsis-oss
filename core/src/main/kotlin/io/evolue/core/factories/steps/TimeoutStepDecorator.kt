package io.evolue.core.factories.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 * Decorator of a step, which generates an error with the timeout is reached before the end of the operation.
 *
 * @author Eric Jessé
 */
class TimeoutStepDecorator<I, O>(
    private val timeout: Duration,
    private val decorated: Step<I, O>,
    private val meterRegistry: MeterRegistry
) : Step<I, O>, StepExecutor {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = decorated.next

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        try {
            withTimeout(timeout.toMillis()) {
                executeStep(decorated, context)
            }
        } catch (e: TimeoutCancellationException) {
            meterRegistry.counter("step-${id}-timeout", "minion", context.minionId).increment()
            context.exhausted = true
            throw e
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}
