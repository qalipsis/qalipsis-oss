/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.report

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Report of a single step within a [ScenarioReport].
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Details about execution report of a step",
    title = "Step-level execution details within a scenario report"
)
data class StepReport(
    @field:Schema(description = "Name of the step")
    val name: StepName,

    @field:Schema(description = "Identifier of the DAG owning this step")
    val dagId: DirectedAcyclicGraphName,

    @field:Schema(description = "Whether the step is subject to load injection")
    val isUnderLoad: Boolean,

    @field:Schema(description = "Whether the step was successfully initialized")
    val initialized: Boolean,

    @field:Schema(description = "Error message when initialization failed", required = false)
    val initializationError: String? = null,

    @field:Schema(description = "Total count of successful executions for this step")
    val successfulExecutions: Long = 0L,

    @field:Schema(description = "Total count of failed executions for this step")
    val failedExecutions: Long = 0L
)
