package io.qalipsis.api.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.Slot
import java.util.concurrent.ConcurrentHashMap

/**
 * <p>
 * The Directed Acyclic Graph (or DAG) is a representation of consecutive or parallel vertices for each of the steps
 * to perform locally on a single factory. It supports fan-in (one vertex has several direct ancestors) and fan-out
 * (one vertex is direct ancestor of several ones). However, a limitation exists on the construction of the
 * DAGs: a vertex/step cannot be direct descendant of a transitive ancestor.
 * </p>
 *
 * <p>
 * Since for technical and security reason some steps cannot be run anywhere (like reading a database or accessing
 * a protected resource), the DAGs of a single minion can be distributed and executed on different factories.
 * A scenario is composed of at least one or more DAGs.
 * </p>
 */
data class DirectedAcyclicGraph(

    /**
     * ID of the Directed Acyclic Graph.
     */
    val id: DirectedAcyclicGraphId,

    /**
     * Scenario to which the DAG owns.
     */
    val scenario: Scenario,

    /**
     * Defines if the DAG is the root of scenario, in which case it has to wait for directive to be started.
     */
    val isRoot: Boolean,

    /**
     * Defines if the DAG is part of the tree receiving the load of all the minions.
     */
    val isUnderLoad: Boolean,

    /**
     * Defines if the DAG runs for a singleton minion, in which case it is not driven by start directive.
     */
    val isSingleton: Boolean,

    /**
     * Selectors to correlate on which factory the DAG can be executed.
     */
    val selectors: MutableMap<String, String>
) {

    /**
     * First step to execute when running the DAG onto a minion.
     */
    var rootStep: Slot<Step<*, *>> = Slot()

    /**
     * Steps are stored into slots, because they might be decorated or wrapped during the initialization process.
     */
    private val steps = ConcurrentHashMap<StepId, Slot<Step<*, *>>>()

    val stepsCount: Int
        get() = steps.size

    /**
     * Verifies if the step belongs to the DAG.
     */
    fun hasStep(stepId: StepId) = steps.containsKey(stepId)

    suspend fun addStep(step: Step<*, *>) {
        if (rootStep.isEmpty()) {
            rootStep.also {
                steps[step.id] = it
            }
        } else {
            steps.computeIfAbsent(step.id) { Slot() }
        }.set(step)
        scenario.addStep(this, step)
    }

    suspend fun findStep(stepId: StepId) = scenario.findStep(stepId)
}
