package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Decorator of a step, aimaing at executing it a given number of times or indefinitely.
 *
 * @author Eric Jessé
 */
class IterativeStepDecorator<I, O>(
    private val iterations: Long = 1,
    private val delay: Duration = Duration.ZERO,
    private val decorated: Step<I, O>
) : Step<I, O>, StepExecutor {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = decorated.next

    var delayMillis = delay.toMillis()

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        val internalContext = StepContext(
            input = Channel<I>(Channel.CONFLATED),
            output = context.output,
            scenarioId = context.scenarioId,
            directedAcyclicGraphId = context.directedAcyclicGraphId,
            errors = context.errors, // Really share the same instance.
            exhausted = context.exhausted,
            minionId = context.minionId,
            parentStepId = context.parentStepId,
            stepId = context.stepId
        )
        var remainingIterations = iterations
        val input = context.input.receive()
        while (remainingIterations > 0) {
            log.trace("Executing iteration ${internalContext.stepIterationIndex}")
            (internalContext.input as Channel<I>).send(input)
            executeStep(decorated, internalContext)
            internalContext.stepIterationIndex++
            remainingIterations--
            if (delayMillis > 0 && remainingIterations > 0) {
                log.trace("Applying delay of ${delay} before next iteration")
                delay(delay.toMillis())
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}