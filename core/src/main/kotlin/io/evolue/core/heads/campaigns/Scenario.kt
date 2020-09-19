package io.evolue.core.heads.campaigns

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.core.heads.persistence.Entity

/**
 * Contains all the details of a [io.evolue.api.orchestration.Scenario] that are relevant for the head.
 *
 * @author Eric Jessé
 */
data class HeadScenario(
    override var id: ScenarioId,
    val minionsCount: Int,
    val directedAcyclicGraphs: List<HeadDirectedAcyclicGraph>
) : Entity<ScenarioId>

/**
 * Entity to provide metadata of a directed acyclic graph for the head.
 */
data class HeadDirectedAcyclicGraph(
    val id: DirectedAcyclicGraphId,
    val singleton: Boolean = false,
    val scenarioStart: Boolean = false,
    val numberOfSteps: Int = 0
)
