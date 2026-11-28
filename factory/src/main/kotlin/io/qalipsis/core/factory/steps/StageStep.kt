/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.Step
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factory.context.StepContextImpl
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Step grouping several steps, providing a convenient way of iterating a complete sub-graph, waiting for the last step
 * to be completed before starting again.
 *
 * @author Eric Jessé
 */
class StageStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val minionsKeeper: MinionsKeeper
) : AbstractStep<I, O>(id, retryPolicy), RunnerAware {

    private var head: Step<I, *>? = null

    override lateinit var runner: Runner

    override suspend fun init() {
        super.init()
        head?.let { init(it) }
    }

    private suspend fun init(step: Step<*, *>) {
        step.init()
        step.next.forEach { init(it) }
    }

    override suspend fun start(context: StepStartStopContext) {
        super.start(context)
        head?.let { start(it, context) }
    }

    private suspend fun start(step: Step<*, *>, context: StepStartStopContext) {
        step.start(context)
        step.next.forEach { start(it, context) }
    }

    override suspend fun stop(context: StepStartStopContext) {
        super.stop(context)
        head?.let { stop(it, context) }
    }

    private suspend fun stop(step: Step<*, *>, context: StepStartStopContext) {
        step.stop(context)
        step.next.forEach { stop(it, context) }
    }

    override suspend fun destroy() {
        super.destroy()
        head?.let { destroy(it) }
    }

    private suspend fun destroy(step: Step<*, *>) {
        step.destroy()
        step.next.forEach { destroy(it) }
    }

    override fun addNext(nextStep: Step<*, *>) {
        // addNext is called twice: once to add the head step, and once via the GroupEndProxy, to really
        // add a next step to the group.
        if (head == null) {
            // If no head was set yet, the "next" is actually not added as next, but as head.
            @Suppress("UNCHECKED_CAST")
            head = nextStep as Step<I, *>
        } else {
            super.addNext(nextStep)
        }
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        val output = Channel<Any?>(Channel.UNLIMITED)
        val input = Channel<I>(1)
        val innerContext = context.duplicate(inputChannel = input, outputChannel = output)
        input.send(context.receive())

        val failure = AtomicBoolean(false)

        try {
            // The first step of the group is executed.
            runner.execute(minion, head!!, innerContext) { ctx ->
                log.trace { "Consuming the tail context $ctx" }
                if (!context.isExhausted && !ctx.isExhausted && ctx.generatedOutput) {
                    // When the latest step of the group is executed, its output is forwarded to the overall context.
                    @Suppress("UNCHECKED_CAST")
                    (ctx as? StepContextImpl<*, *>)?.consume { outputRecord ->
                        context.send(outputRecord.value as O)
                    }
                } else if (ctx.isExhausted) {
                    failure.set(true)
                    log.trace { "The stage '${this.name}' finished with error(s): ${ctx.errors.joinToString { it.message }}" }
                    // Errors have to be forwarded.
                    ctx.errors.forEach(context::addError)
                }
            }?.join()
        } finally {
            input.cancel()
            output.close()
        }

        if (failure.get()) {
            val lastError = context.errors.lastOrNull()
            throw StepExecutionException(lastError?.message ?: "Stage '${this.name}' failed")
        }
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // This method should never be called.
        throw NotImplementedError()
    }

    override suspend fun complete(completionContext: CompletionContext) {
        // The steps following the stage might complete the minion and shut it down concurrently, once the tail
        // already completed the steps of the stage: there is then nothing left to complete.
        val minion = minionsKeeper.find(completionContext.minionId)
        if (minion != null) {
            log.trace { "Completing the stage '$name' for the minion ${completionContext.minionId}" }
            runner.complete(minion, head!!, completionContext)?.join()
            log.trace { "Stage '$name' for the minion ${completionContext.minionId} is completed" }
        } else {
            log.trace { "The minion ${completionContext.minionId} was already shut down, the stage '$name' is completed" }
        }
    }

    companion object {

        private val log = logger()
    }
}
