package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.Step
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factories.context.StepContextImpl
import io.qalipsis.core.factories.orchestration.Runner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Step grouping several steps, providing a convenient way of iterating a complete sub-graph, waiting for the last step
 * to be completed before starting again.
 *
 * @author Eric Jessé
 */
internal class StageStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
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
        val innerContext = context.duplicate(newOutput = output)

        var failure = false

        // The first step of the group is executed.
        runner.execute(minion, head!!, innerContext) { ctx ->
            log.trace { "Consuming the tail context $ctx" }
            if (!context.isExhausted && !ctx.isExhausted) {
                // When the latest step of the group is executed, its output is forwarded to the overall context.
                for (outputRecord in (ctx as StepContextImpl).output as ReceiveChannel<*>) {
                    @Suppress("UNCHECKED_CAST")
                    context.send(outputRecord as O)
                }
            } else {
                failure = true
                log.trace { "The stage ${this.id} finished with error(s): ${ctx.errors.joinToString { it.message }}" }
                // Errors have to be forwarded.
                ctx.errors.forEach(context::addError)
            }
        }?.join()

        if (failure) {
            throw StepExecutionException()
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
