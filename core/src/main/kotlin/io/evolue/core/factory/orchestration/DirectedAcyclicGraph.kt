package io.evolue.core.factory.orchestration

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.steps.Step

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
internal data class DirectedAcyclicGraph(
    /**
     * ID of the Directed Acyclic Graph.
     */
    val id: DirectedAcyclicGraphId,
    /**
     * ID of the scenario to which the DAG refers.
     */
    val scenarioId: ScenarioId,
    /**
     * First steps to execute in parallel when running the DAG onto a minion.
     */
    val rootSteps: MutableList<Step<Unit, *>> = mutableListOf(),
    /**
     * Defines if the DAG is the start of scenario, in which case it has to wait for directive to be started.
     */
    val scenarioStart: Boolean,
    /**
     * Defines if the DAG runs for a singleton minion, in which case it is not driven by start directive.
     */
    val singleton: Boolean
)