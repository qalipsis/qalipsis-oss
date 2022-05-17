package io.qalipsis.core.campaigns

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.persistence.InMemoryEntity
import kotlinx.serialization.Serializable

/**
 * Contains all the details of a [io.qalipsis.api.orchestration.Scenario] that are relevant for the head.
 *
 * @author Eric Jessé
 */
@Serializable
data class ScenarioSummary(
    override var name: ScenarioName,
    val minionsCount: Int,
    val directedAcyclicGraphs: List<DirectedAcyclicGraphSummary>
) : InMemoryEntity<ScenarioName>

/**
 * Entity to provide metadata of a directed acyclic graph for the head.
 */
@Serializable
data class DirectedAcyclicGraphSummary(
    val name: DirectedAcyclicGraphName,
    val isSingleton: Boolean = false,
    val isRoot: Boolean = false,
    val isUnderLoad: Boolean = false,
    val numberOfSteps: Int = 0,
    val tags: Map<String, String> = emptyMap()
)
