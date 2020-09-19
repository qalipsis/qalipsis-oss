package io.evolue.core.factories.steps

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
 * Decorator of a step, aiming at executing it a given number of times or indefinitely.
 *
 * @author Eric Jessé
 */
class IterativeStepDecorator<I, O>(
    private val iterations: Long = 1,
    delay: Duration = Duration.ZERO,
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
            campaignId = context.campaignId,
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
            val currentIteration = internalContext.stepIterationIndex
            (internalContext.input as Channel<I>).send(input)
            log.trace("Executing iteration ${currentIteration} for context ${context}")
            executeStep(decorated, internalContext)
            internalContext.stepIterationIndex = internalContext.stepIterationIndex + 1
            remainingIterations--
            log.trace("Executed iteration ${currentIteration} for context ${context}, remaining ${remainingIterations}")
            if (delayMillis > 0 && remainingIterations > 0) {
                log.trace("Applying delay of ${delayMillis} ms before next iteration")
                delay(delayMillis)
            }
        }
        log.trace("End of the iterations for context ${context}")
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
