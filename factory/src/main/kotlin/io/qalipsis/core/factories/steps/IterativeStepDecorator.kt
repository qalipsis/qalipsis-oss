package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import io.qalipsis.core.factories.context.StepContextImpl
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
    override val decorated: Step<I, O>
) : Step<I, O>, StepExecutor, StepDecorator<I, O> {

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override val next = decorated.next

    private var delayMillis = delay.toMillis()

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        val inputValue = context.receive()
        // Create a conflated channel to allow next iteration even if the channel was not consumed.
        val internalInputChannel = Channel<I>(Channel.CONFLATED)
        val internalContext = context.duplicate(internalInputChannel, (context as StepContextImpl).output)

        val isOutputContextTail = context.isTail
        context.isTail = false

        var remainingIterations = iterations
        var currentIteration = 0L
        while (remainingIterations > 0) {
            internalContext.stepIterationIndex = currentIteration

            internalContext.isTail = isOutputContextTail && remainingIterations == 1L
            context.isTail = internalContext.isTail

            // Provide the input value again for each iteration.
            internalInputChannel.send(inputValue)
            log.trace { "Executing iteration $currentIteration for context $context" }
            try {
                executeStep(minion, decorated, internalContext)
                internalContext.errors.forEach(context::addError)
            } catch (t: Throwable) {
                log.debug(t) { "The repeated step ${decorated.id} failed: ${t.message}" }
                context.isTail = isOutputContextTail
                throw t
            }
            if (!internalContext.isExhausted) {
                remainingIterations--
                currentIteration++
                log.trace { "Executed iteration $currentIteration for context ${context}, remaining $remainingIterations" }

                if (delayMillis > 0 && remainingIterations > 0) {
                    log.trace { "Applying delay of $delayMillis ms before next iteration" }
                    delay(delayMillis)
                } else if (remainingIterations > 0) {
                    // Force a delay to have a suspension point.
                    delay(1)
                }
            } else {
                log.debug { "The repeated step ${decorated.id} failed." }
                context.isExhausted = true
                context.isTail = isOutputContextTail
            }
        }
        log.trace { "End of the iterations for context $context" }
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
