package io.qalipsis.core.campaigns

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.persistence.InMemoryEntity
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable


/**
 * Contains all the details of a [io.qalipsis.api.orchestration.Scenario] that are relevant for the head.
 *
 * @author Eric Jessé
 */
@Introspected
@Serializable
@Schema(
    name = "ScenarioSummary",
    title = "Scenario summary of QALIPSIS",
    description = "Details of the scenario as persisted into QALIPSIS"
)
data class ScenarioSummary(
    @field:Schema(description = "Name of the Scenario")
    override var name: ScenarioName,
    @field:Schema(description = "Counts of minions that will be assigned to the scenario")
    val minionsCount: Int,
    @field:Schema(description = "The list of directed acyclic graphs summaries of the scenario")
    val directedAcyclicGraphs: List<DirectedAcyclicGraphSummary>,
    @field:Schema(description = "The name of the ramp up strategy with default value 'user-defined'")
    val rampUpStrategyName: String = "user-defined"
) : InMemoryEntity<ScenarioName>

/**
 * Entity to provide metadata of a directed acyclic graph for the head.
 */
@Introspected
@Serializable
@Schema(
    name = "DirectedAcyclicGraphSummary",
    title = "Directed acyclic graphs summary of the scenario of QALIPSIS",
    description = "Details of the directed acyclic graphs of the scenario as persisted into QALIPSIS"
)
data class DirectedAcyclicGraphSummary(
    @field:Schema(
        description = "The name of the directed acyclis graph"
    )
    val name: DirectedAcyclicGraphName,
    @field:Schema(
        description = "The flag is the directed acyclic graph is singleton or not with default value 'false'"
    )
    val isSingleton: Boolean = false,
    @field:Schema(
        description = "The flag is the directed acyclic graph is root one or not with default value 'false'"
    )
    val isRoot: Boolean = false,
    @field:Schema(
        description = "The flag is the directed acyclic graph is under load or not with default value 'false'"
    )
    val isUnderLoad: Boolean = false,
    @field:Schema(
        description = "The number of steps in directed acyclic graph with default value '0'"
    )
    val numberOfSteps: Int = 0,
    @field:Schema(
        description = "The name of the ramp up strategy with default value 'user-defined'"
    )
    val selectors: Map<String, String> = emptyMap()
)
