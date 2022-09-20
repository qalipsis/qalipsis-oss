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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

/**
 * Report of a test campaign for a given scenario.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Details about execution report of a completed scenario",
    title = "Details for the scenario report to retrieve from the REST endpoint"
)
data class ScenarioExecutionDetails(

    @field:Schema(description = "Identifier of the scenario")
    @field:NotBlank
    val id: String,

    @field:Schema(description = "Display name of the scenario")
    @field:NotBlank
    val name: String,

    @field:Schema(description = "Date and time when the scenario started", required = false)
    val start: Instant?,

    @field:Schema(
        description = "Date and time when the scenario was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Counts of minions when the scenario started", required = false)
    @field:PositiveOrZero
    val startedMinions: Int?,

    @field:Schema(description = "Counts of minions that completed their scenario", required = false)
    @field:PositiveOrZero
    val completedMinions: Int?,

    @field:Schema(description = "Counts of minions that successfully completed their scenario", required = false)
    @field:PositiveOrZero
    val successfulExecutions: Int?,

    @field:Schema(description = "Counts of minions that failed to execute their scenario", required = false)
    @field:PositiveOrZero
    val failedExecutions: Int?,

    @field:Schema(description = "Overall execution status of the scenario")
    val status: ExecutionStatus,

    @field:Schema(description = "The list of the report messages for the scenario")
    @field:Valid
    val messages: List<ReportMessage>
)
