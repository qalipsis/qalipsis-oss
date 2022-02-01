package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.Job

/**
 * Core component in charge of executing all the steps on all the minions.
 *
 * @author Eric Jessé
 */
interface Runner {

    /**
     * Executes the dag onto the specified minion.
     */
    suspend fun run(minion: Minion, dag: DirectedAcyclicGraph)

    /**
     * Launches the execution of a step onto a minion.
     *
     * @param minion the minion to launch
     * @param step the first step of the chain to execute
     * @param ctx the initial step context
     * @param jobsCounter the suspended counter of the running jobs, suspends calls to [SuspendedCountLatch.await] while there are running or scheduled steps
     * @param consumer action to execute on the output of the last steps of the chain, it is started asynchronously before the last step execute: the channel will provide the execution status (open or closed)
     */
    suspend fun launch(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>,
                       jobsCounter: SuspendedCountLatch? = null,
                       consumer: (suspend (ctx: StepContext<*, *>) -> Unit)? = null)

    /**
     * Executes a single step onto the specified context and triggers the next steps asynchronously in different coroutines.
     *
     * @param minion the minion to launch
     * @param step the first step of the chain to execute
     * @param ctx the initial step context
     * @param consumer action to execute on the output of the last steps of the chain, it is started asynchronously before the last step execute: the channel will provide the execution status (open or closed)
     */
    suspend fun execute(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>,
                        consumer: (suspend (ctx: StepContext<*, *>) -> Unit)?): Job?
}
