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
import javax.validation.constraints.PositiveOrZero

/**
 * External representation of a campaign.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Campaign details",
    title = "Details of a running or completed campaign"
)
internal data class Campaign(
    @field:Schema(description = "Last change of the campaign", required = true)
    val version: Instant,

    @field:Schema(description = "Unique identifier of the campaign", required = true)
    val key: String,

    @field:Schema(description = "Display name of the campaign", required = true)
    val name: String,

    @field:Schema(
        description = "Speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation",
        required = true
    )
    val speedFactor: Double,

    @field:Schema(description = "Counts of minions scheduled to be started", required = false)
    @field:PositiveOrZero
    val scheduledMinions: Int?,

    @field:Schema(description = "Instant when the campaign should be aborted", required = false)
    val timeout: Instant? = null,

    @field:Schema(
        description = "Specifies whether the campaign should generate a failure (true) when the timeout is reached",
        required = false
    )
    val hardTimeout: Boolean? = null,

    @field:Schema(description = "Date and time when the campaign started", required = true)
    val start: Instant?,

    @field:Schema(
        description = "Date and time when the campaign was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Overall execution status of the campaign when completed", required = false)
    val result: ExecutionStatus?,

    @field:Schema(description = "Name of the user, who created the campaign", required = false)
    val configurerName: String?,

    @field:Schema(description = "Scenarios being part of the campaign", required = true)
    val scenarios: Collection<Scenario>
)
