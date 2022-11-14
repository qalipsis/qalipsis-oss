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
internal open class Campaign(
    @field:Schema(description = "Last change of the campaign", required = true)
    val version: Instant,

    @field:Schema(description = "Unique identifier of the campaign", required = true)
    val key: String,

    @field:Schema(description = "Creation time of the campaign", required = true)
    val creation: Instant,

    @field:Schema(description = "Display name of the campaign", required = true)
    val name: String,

    @field:Schema(
        description = "Speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation",
        required = true,
        example = "1.0"
    )
    val speedFactor: Double,

    @field:Schema(description = "Counts of minions scheduled to be started", required = false, example = "100")
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
    val status: ExecutionStatus,

    @field:Schema(description = "Name of the user, who created the campaign", required = false, example = "John Doe")
    val configurerName: String?,

    @field:Schema(description = "Name of the user, who aborted the campaign", required = false, example = "John Doe")
    val aborterName: String? = null,

    @field:Schema(description = "Scenarios being part of the campaign", required = true)
    val scenarios: Collection<Scenario>,

    @field:Schema(description = "Complete configuration of the campaign")
    val configuration: CampaignConfiguration? = null
) {

    fun copy(
        version: Instant = this.version,
        key: String = this.key,
        creation: Instant = this.creation,
        name: String = this.name,
        speedFactor: Double = this.speedFactor,
        scheduledMinions: Int? = this.scheduledMinions,
        timeout: Instant? = this.timeout,
        hardTimeout: Boolean? = this.hardTimeout,
        start: Instant? = this.start,
        end: Instant? = this.end,
        status: ExecutionStatus = this.status,
        configurerName: String? = this.configurerName,
        aborterName: String? = this.aborterName,
        scenarios: Collection<Scenario> = this.scenarios,
        configuration: CampaignConfiguration? = this.configuration,
    ) = Campaign(
        version = version,
        key = key,
        creation = creation,
        name = name,
        speedFactor = speedFactor,
        scheduledMinions = scheduledMinions,
        timeout = timeout,
        hardTimeout = hardTimeout,
        start = start,
        end = end,
        status = status,
        configurerName = configurerName,
        aborterName = aborterName,
        scenarios = scenarios,
        configuration = configuration
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Campaign

        if (version != other.version) return false
        if (key != other.key) return false
        if (creation != other.creation) return false
        if (name != other.name) return false
        if (speedFactor != other.speedFactor) return false
        if (scheduledMinions != other.scheduledMinions) return false
        if (timeout != other.timeout) return false
        if (hardTimeout != other.hardTimeout) return false
        if (start != other.start) return false
        if (end != other.end) return false
        if (status != other.status) return false
        if (configurerName != other.configurerName) return false
        if (aborterName != other.aborterName) return false
        if (scenarios != other.scenarios) return false
        if (configuration != other.configuration) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = version.hashCode()
        result1 = 31 * result1 + key.hashCode()
        result1 = 31 * result1 + creation.hashCode()
        result1 = 31 * result1 + name.hashCode()
        result1 = 31 * result1 + speedFactor.hashCode()
        result1 = 31 * result1 + (scheduledMinions ?: 0)
        result1 = 31 * result1 + (timeout?.hashCode() ?: 0)
        result1 = 31 * result1 + (hardTimeout?.hashCode() ?: 0)
        result1 = 31 * result1 + (start?.hashCode() ?: 0)
        result1 = 31 * result1 + (end?.hashCode() ?: 0)
        result1 = 31 * result1 + status.hashCode()
        result1 = 31 * result1 + (configurerName?.hashCode() ?: 0)
        result1 = 31 * result1 + (aborterName?.hashCode() ?: 0)
        result1 = 31 * result1 + scenarios.hashCode()
        result1 = 31 * result1 + (configuration?.hashCode() ?: 0)
        return result1
    }

    override fun toString(): String {
        return "Campaign(version=$version, key='$key', creation=$creation, name='$name', speedFactor=$speedFactor, scheduledMinions=$scheduledMinions, timeout=$timeout, hardTimeout=$hardTimeout, start=$start, end=$end, result=$status, configurerName=$configurerName, aborterName=$aborterName, scenarios=$scenarios, configuration=$configuration)"
    }


}
