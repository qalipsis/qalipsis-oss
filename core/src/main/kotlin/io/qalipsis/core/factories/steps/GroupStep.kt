package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factories.orchestration.Runner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Step grouping several steps, providing a convenient way of iterating a complete sub-graph, waiting for the last step
 * to be completed before starting again.
 *
 * @author Eric Jessé
 */
internal class GroupStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
) : AbstractStep<I, O>(id, retryPolicy), RunnerAware {

    private var head: Step<I, *>? = null

    override lateinit var runner: Runner

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
        val countLatch = SuspendedCountLatch()

        // The first step of the group is executed.
        runner.launch(minion, head!!, innerContext, countLatch) { ctx ->
            if (!context.isExhausted && !ctx.isExhausted) {
                // When the latest step of the group is executed, its output is forwarded to the overall context.
                for (outputRecord in ctx.output as ReceiveChannel<*>) {
                    @Suppress("UNCHECKED_CAST")
                    context.output.send(outputRecord as O)
                }
            } else {
                // Errors have to be forwarded.
                context.errors.addAll(ctx.errors)
                context.isExhausted = true
            }
        }
        countLatch.awaitActivity().await()
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
