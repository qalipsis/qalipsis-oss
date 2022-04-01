package io.qalipsis.core.factory.init

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factory.steps.DeadEndStep

internal interface DagTransitionStepFactory {

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     */
    fun createDeadEnd(stepName: StepName, sourceDagId: DirectedAcyclicGraphName): DeadEndStep<*>

    /**
     * Creates a new step in charge or managing the transition between two directed acyclic graphs.
     */
    fun createTransition(
        stepName: StepName,
        sourceDagId: DirectedAcyclicGraphName,
        targetDagId: DirectedAcyclicGraphName
    ): Step<*, *>

}