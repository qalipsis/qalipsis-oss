package io.qalipsis.core.factories.steps

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 * Decorator of a step, which generates an error with the timeout is reached before the end of the operation.
 *
 * @author Eric Jessé
 */
internal class TimeoutStepDecorator<I, O>(
    private val timeout: Duration,
    override val decorated: Step<I, O>,
    private val meterRegistry: MeterRegistry
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = decorated.next

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        try {
            withTimeout(timeout.toMillis()) {
                executeStep(minion, decorated, context)
            }
        } catch (e: TimeoutCancellationException) {
            meterRegistry.counter("step-${id}-timeout", "minion", context.minionId).increment()
            context.isExhausted = true
            throw e
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }


}
