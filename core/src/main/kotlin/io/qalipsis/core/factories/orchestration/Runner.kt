package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.Step

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
     */
    suspend fun launch(minion: Minion, step: Step<*, *>, ctx: StepContext<*, *>)

}