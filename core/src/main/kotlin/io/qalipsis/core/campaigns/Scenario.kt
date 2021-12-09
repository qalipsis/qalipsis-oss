package io.qalipsis.core.campaigns

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.persistence.Entity
import kotlinx.serialization.Serializable

/**
 * Contains all the details of a [io.qalipsis.api.orchestration.Scenario] that are relevant for the head.
 *
 * @author Eric Jessé
 */
@Serializable
data class ScenarioSummary(
    override var id: ScenarioId,
    val minionsCount: Int,
    val directedAcyclicGraphs: List<DirectedAcyclicGraphSummary>
) : Entity<ScenarioId>

/**
 * Entity to provide metadata of a directed acyclic graph for the head.
 */
@Serializable
data class DirectedAcyclicGraphSummary(
    val id: DirectedAcyclicGraphId,
    val isSingleton: Boolean = false,
    val isRoot: Boolean = false,
    val isUnderLoad: Boolean = false,
    val numberOfSteps: Int = 0,
    val selectors: Map<String, String> = emptyMap()
)
