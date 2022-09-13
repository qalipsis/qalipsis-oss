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
import io.qalipsis.api.context.ScenarioName
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
data class ScenarioReport(
    @field:Schema(description = "Unique identifier of the campaign")
    @field:NotBlank
    val campaignKey: CampaignKey,

    @field:Schema(description = "Identifier of the scenario")
    @field:NotBlank
    val scenarioName: ScenarioName,

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
