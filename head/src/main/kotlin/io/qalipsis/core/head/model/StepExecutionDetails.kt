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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.TimeSeriesMeter
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

/**
 * Execution details of a single step within a scenario, used in the campaign HTML report.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Details about the execution of a step",
    title = "Execution details for a step within a scenario report"
)
data class StepExecutionDetails(

    @field:Schema(description = "Name of the step as defined in the scenario")
    @field:NotBlank
    val name: String,

    @field:Schema(description = "Technical type of the step, e.g. HTTP, JAVASCRIPT, KAFKA", required = false)
    val type: String? = null,

    @field:Schema(description = "Counts of minions that started the execution of this step", required = false)
    @field:PositiveOrZero
    val startedMinions: Int? = null,

    @field:Schema(description = "Counts of step executions that completed successfully", required = false)
    @field:PositiveOrZero
    val successfulExecutions: Long? = null,

    @field:Schema(description = "Counts of step executions that failed", required = false)
    @field:PositiveOrZero
    val failedExecutions: Long? = null,

    @field:Schema(description = "Overall execution status of the step")
    val status: ExecutionStatus,

    @field:Schema(description = "Report messages produced by this step")
    @field:Valid
    val messages: List<ReportMessage> = emptyList(),

    @field:Schema(description = "Aggregated meters produced by this step during the campaign execution")
    val meters: List<TimeSeriesMeter> = emptyList()
) {
    val totalExecutions: Long get() = (successfulExecutions ?: 0L) + (failedExecutions ?: 0L)

    val notExecuted: Boolean get() = totalExecutions == 0L
}
