/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.report

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.CampaignKey
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
data class CampaignReport(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:NotBlank
    val campaignKey: CampaignKey,

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
    val scenariosReports: List<ScenarioReport> = emptyList()
)
