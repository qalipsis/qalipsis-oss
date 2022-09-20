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
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

/**
 * Aggregated report of all the scenarios of a campaign.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Report of campaign execution",
    title = "Details of the execution of a completed or running campaign and its scenario"
)
data class CampaignExecutionDetails(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:NotBlank
    val key: String,

    @field:Schema(description = "Display name of the campaign")
    @field:NotBlank
    val name: String,

    @field:Schema(description = "Date and time when the campaign started", required = false)
    val start: Instant?,

    @field:Schema(
        description = "Date and time when the campaign was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Counts of minions scheduled to be started", required = false)
    @field:PositiveOrZero
    val scheduledMinions: Int?,

    @field:Schema(description = "Counts of minions when the campaign started", required = false)
    @field:PositiveOrZero
    val startedMinions: Int?,

    @field:Schema(description = "Instant when the campaign should be aborted", required = false)
    val timeout: Instant? = null,

    @field:Schema(
        description = "Specifies whether the campaign should generate a failure (true) when the timeout is reached",
        required = false
    )
    val hardTimeout: Boolean? = null,

    @field:Schema(description = "Counts of minions that completed the campaign", required = false)
    @field:PositiveOrZero
    val completedMinions: Int?,

    @field:Schema(description = "Counts of minions that successfully completed the campaign", required = false)
    @field:PositiveOrZero
    val successfulExecutions: Int?,

    @field:Schema(description = "Counts of minions that failed to execute the campaign", required = false)
    @field:PositiveOrZero
    val failedExecutions: Int?,

    @field:Schema(description = "Overall execution status of the campaign")
    val status: ExecutionStatus,

    @field:Schema(description = "The list of the scenario reports for the campaign")
    @field:Valid
    val scenariosReports: List<ScenarioExecutionDetails> = emptyList()
)
