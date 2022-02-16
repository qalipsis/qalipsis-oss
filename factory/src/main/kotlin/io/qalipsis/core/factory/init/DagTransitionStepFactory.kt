package io.qalipsis.core.factory.init

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factory.steps.DeadEndStep

internal interface DagTransitionStepFactory {

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     */
    fun createDeadEnd(stepId: StepId, sourceDagId: DirectedAcyclicGraphId): DeadEndStep<*>

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     */
    fun createTransition(
        stepId: StepId,
        sourceDagId: DirectedAcyclicGraphId,
        targetDagId: DirectedAcyclicGraphId
    ): Step<*, *>

}