/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
    description = "Details of a scenario that can be executed in QALIPSIS"
)
data class ScenarioSummary(
    @field:Schema(description = "Name of the Scenario")
    override var name: ScenarioName,
    @field:Schema(description = "Default count of minions executing the scenario")
    val minionsCount: Int,
    @field:Schema(description = "The list of directed acyclic graphs structuring the workflow of the scenario")
    val directedAcyclicGraphs: List<DirectedAcyclicGraphSummary>,
    @field:Schema(description = "The name of the ramp up strategy to start the minions in the scenario, defaults to 'user-defined'")
    val executionProfileName: String = "user-defined"
) : InMemoryEntity<ScenarioName>

/**
 * Entity to provide metadata of a directed acyclic graph for the head.
 */
@Introspected
@Serializable
@Schema(
    name = "DirectedAcyclicGraphSummary",
    title = "Directed acyclic graphs summary of the scenario of QALIPSIS, also designated as DAG",
    description = "A directed acyclic graphs contains a linear set of steps and structures the execution workflow of a scenario"
)
data class DirectedAcyclicGraphSummary(
    @field:Schema(
        description = "The name of the directed acyclic graph, unique in a scenario"
    )
    val name: DirectedAcyclicGraphName,
    @field:Schema(
        description = "Defines whether the DAG executes only singleton steps, such as poll steps for example"
    )
    val isSingleton: Boolean = false,
    @field:Schema(
        description = "Defines whether the DAG is linked to root of the scenario - true, or is following another DAG - false."
    )
    val isRoot: Boolean = false,
    @field:Schema(
        description = "Defines whether the DAG executes minions under load, implying that its steps can be executed a massive count of times"
    )
    val isUnderLoad: Boolean = false,
    @field:Schema(
        description = "The number of actual - whether declared in the scenario or technically created by QALIPSIS - steps contains in the DAG"
    )
    val numberOfSteps: Int = 0,
    @field:Schema(
        description = "Pairs of key/values that additionally describes the DAG"
    )
    val tags: Map<String, String> = emptyMap()
)
