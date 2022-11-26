/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepContext.StepOutputRecord
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory
import kotlinx.coroutines.channels.consumeEach
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

    override val name: StepName
        get() = decorated.name

    override var retryPolicy: RetryPolicy? = null

    override val next = decorated.next

    /**
     * Force a delay to have a suspension point.
     */
    private var delayMillis = delay.toMillis().coerceAtLeast(1)

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        val inputValue = context.receive()

        // Unmark the context as tail until the very last record.
        val isOuterContextTail = context.isTail
        context.isTail = false

        // Reusing the same channel for all the iterations.
        val internalInputChannel = Channel<I>(1)

        var remainingIterations = iterations
        var repetitionIndex = 0L
        while (remainingIterations > 0) {
            log.trace { "Executing iteration $repetitionIndex for context $context" }

            val internalOutputChannel = Channel<StepOutputRecord<O>>(Factory.UNLIMITED)
            val innerContext =
                context.duplicate(
                    inputChannel = internalInputChannel,
                    outputChannel = internalOutputChannel,
                    stepIterationIndex = repetitionIndex
                )
            innerContext.isTail = isOuterContextTail && remainingIterations == 1L

            // Provides the input value again for each iteration.
            if (internalInputChannel.isEmpty) {
                internalInputChannel.send(inputValue)
            }
            try {
                executeStep(minion, decorated, innerContext)
                innerContext.errors.forEach(context::addError)
                forwardOutput(context, internalOutputChannel, innerContext)
            } catch (t: Throwable) {
                // Resets the isTail flag before leaving.
                context.isTail = isOuterContextTail
                log.debug(t) { "The repeated step ${decorated.name} failed: ${t.message}" }
                throw t
            }

            if (innerContext.isExhausted) {
                log.trace { "Stopping the iteration, because the inner step failed" }
                remainingIterations = 0
                // Resets the isTail flag before leaving.
                context.isTail = isOuterContextTail
                log.debug { "The repeated step ${decorated.name} failed." }
                context.isExhausted = true
            } else if (!innerContext.generatedOutput && innerContext.isTail) {
                remainingIterations = 0
                log.trace { "Stopping the iteration, because the inner step forced the context to be a tail" }
            } else {
                remainingIterations--
                log.trace { "Executed iteration ${innerContext.stepIterationIndex} for context ${context}, remaining $remainingIterations" }

                if (remainingIterations > 0) {
                    repetitionIndex++
                    log.trace { "Waiting $delayMillis ms for context $context" }
                    delay(delayMillis)
                }
            }
        }
        internalInputChannel.cancel()
        context.isTail = isOuterContextTail
        log.trace { "End of the iterations for context $context" }
    }

    private suspend fun forwardOutput(
        outerContext: StepContext<I, O>,
        internalOutputChannel: Channel<StepOutputRecord<O>>,
        innerContext: StepContext<I, O>
    ) {
        internalOutputChannel.close() // Close for send, not for receive.
        if (innerContext.generatedOutput) {
            if (innerContext.isTail) {
                // When dealing with the tails, that's an exception to handle.
                var isTail = false
                val values = mutableListOf<O>()
                internalOutputChannel.consumeEach {
                    values += it.value
                    isTail = isTail || it.isTail
                }
                values.forEachIndexed { index, o ->
                    if (isTail && index == values.size - 1) {
                        outerContext.isTail = true
                    }
                    outerContext.send(o)
                }
            } else {
                internalOutputChannel.consumeEach { outerContext.send(it.value) }
            }
        }
        internalOutputChannel.cancel()
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
