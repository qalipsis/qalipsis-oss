package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Decorator of a step, aiming at executing it a given number of times or indefinitely.
 *
 * @author Eric Jessé
 */
internal class IterativeStepDecorator<I, O>(
    private val iterations: Long = 1,
    delay: Duration = Duration.ZERO,
    private val decorated: Step<I, O>
) : Step<I, O>, StepExecutor {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override val next = decorated.next

    var delayMillis = delay.toMillis()

    override fun addNext(nextStep: Step<*, *>) {
        decorated.addNext(nextStep)
    }

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
    }


    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        val inputValue = context.input.receive()
        // Create a conflated channel to allow next iteration even if the channel was not consumed.
        val internalInputChannel = Channel<I>(Channel.CONFLATED)
        val internalContext = context.duplicate(internalInputChannel, context.output)
        var remainingIterations = iterations
        while (remainingIterations > 0) {
            // Provide the input value again for each iteration.
            internalInputChannel.send(inputValue)
            val currentIteration = internalContext.stepIterationIndex
            log.trace("Executing iteration $currentIteration for context $context")
            executeStep(minion, decorated, internalContext)

            internalContext.stepIterationIndex = internalContext.stepIterationIndex + 1
            remainingIterations--
            log.trace("Executed iteration $currentIteration for context ${context}, remaining $remainingIterations")

            if (delayMillis > 0 && remainingIterations > 0) {
                log.trace("Applying delay of $delayMillis ms before next iteration")
                delay(delayMillis)
            }
        }
        log.trace("End of the iterations for context $context")
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
