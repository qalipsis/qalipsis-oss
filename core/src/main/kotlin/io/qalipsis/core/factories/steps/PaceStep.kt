package io.qalipsis.core.factories.steps

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Step responsible for setting a pace in the workflow of a minion.
 *
 * @author Eric Jessé
 */
internal class PaceStep<I>(
    id: StepId,
    private val specification: (pastPeriodMs: Long) -> Long
) : AbstractStep<I, I>(id, null) {

    private val nextExecutions = ConcurrentHashMap<MinionId, NextExecution>()

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        val nextExecution = nextExecutions[context.minionId] ?: NextExecution(specification(0))
        val waitingDelay = (nextExecution.timestampNanos - System.nanoTime()) / 1_000_000
        if (waitingDelay > 0) {
            log.trace("Waiting for $waitingDelay ms")
            delay(waitingDelay)
        }
        nextExecutions[context.minionId] = NextExecution(specification(nextExecution.periodMs))
        context.send(input)
    }

    private data class NextExecution(val periodMs: Long = 0,
        val timestampNanos: Long = System.nanoTime() + periodMs * 1_000_000)

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
