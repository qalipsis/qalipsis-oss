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
 * Aggregated report of all the scenarios of a campaign.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Report of campaign execution",
    title = "Details of the execution of a completed or running campaign and its scenario"
)
internal class CampaignExecutionDetails(
    version: Instant,
    key: String,
    creation: Instant,
    name: String,
    speedFactor: Double,
    scheduledMinions: Int?,
    softTimeout: Instant? = null,
    hardTimeout: Instant? = null,
    start: Instant?,
    end: Instant?,
    status: ExecutionStatus,
    failureReason: String? = null,
    configurerName: String? = null,
    aborterName: String? = null,
    scenarios: Collection<Scenario>,
    zones: Set<String> = emptySet(),

    @field:Schema(description = "Counts of minions when the campaign started", required = false)
    val startedMinions: Int?,

    @field:Schema(description = "Counts of minions that completed the campaign", required = false)
    val completedMinions: Int?,

    @field:Schema(description = "Counts of steps that successfully completed", required = false)
    val successfulExecutions: Int?,

    @field:Schema(description = "Counts of steps that failed", required = false)
    @field:PositiveOrZero
    val failedExecutions: Int?,

    @field:Schema(description = "Individual details of the scenario executed during the campaign")
    val scenariosReports: List<ScenarioExecutionDetails> = emptyList()
) : Campaign(
    version = version,
    key = key,
    creation = creation,
    name = name,
    speedFactor = speedFactor,
    scheduledMinions = scheduledMinions,
    softTimeout = softTimeout,
    hardTimeout = hardTimeout,
    start = start,
    end = end,
    status = status,
    failureReason = failureReason,
    configurerName = configurerName,
    aborterName = aborterName,
    scenarios = scenarios,
    zones = zones
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CampaignExecutionDetails

        if (!super.equals(other)) return false
        if (startedMinions != other.startedMinions) return false
        if (completedMinions != other.completedMinions) return false
        if (successfulExecutions != other.successfulExecutions) return false
        if (failedExecutions != other.failedExecutions) return false
        if (status != other.status) return false
        if (scenariosReports != other.scenariosReports) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (startedMinions ?: 0)
        result = 31 * result + (completedMinions ?: 0)
        result = 31 * result + (successfulExecutions ?: 0)
        result = 31 * result + (failedExecutions ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + scenariosReports.hashCode()
        return result
    }
}
