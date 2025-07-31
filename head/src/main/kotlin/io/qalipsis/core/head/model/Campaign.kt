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
open class Campaign(
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

    @field:Schema(
        description = "Instant when the campaign should be aborted without generating a failure",
        required = false
    )
    val softTimeout: Instant? = null,

    @field:Schema(
        description = "Instant when the campaign should be aborted as well as generating a failure",
        required = false
    )
    val hardTimeout: Instant? = null,

    @field:Schema(description = "Date and time when the campaign started", required = true)
    val start: Instant?,

    @field:Schema(
        description = "Date and time when the campaign was completed, whether successfully or not",
        required = false
    )
    val end: Instant?,

    @field:Schema(description = "Overall execution status of the campaign when completed", required = false)
    val status: ExecutionStatus,

    @field:Schema(description = "The root cause of the campaign failure", required = false)
    val failureReason: String? = null,

    @field:Schema(description = "Name of the user, who created the campaign", required = false, example = "John Doe")
    val configurerName: String?,

    @field:Schema(description = "Name of the user, who aborted the campaign", required = false, example = "John Doe")
    val aborterName: String? = null,

    @field:Schema(description = "Scenarios being part of the campaign", required = true)
    val scenarios: Collection<Scenario>,

    @field:Schema(description = "Keys of the zones where the campaign was executed", required = false)
    val zones: Set<String> = emptySet()
) {

    fun copy(
        version: Instant = this.version,
        key: String = this.key,
        creation: Instant = this.creation,
        name: String = this.name,
        speedFactor: Double = this.speedFactor,
        scheduledMinions: Int? = this.scheduledMinions,
        softTimeout: Instant? = this.softTimeout,
        hardTimeout: Instant? = this.hardTimeout,
        start: Instant? = this.start,
        end: Instant? = this.end,
        status: ExecutionStatus = this.status,
        failureReason: String? = null,
        configurerName: String? = this.configurerName,
        aborterName: String? = this.aborterName,
        scenarios: Collection<Scenario> = this.scenarios
    ) = Campaign(
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
        scenarios = scenarios
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
        if (softTimeout != other.softTimeout) return false
        if (hardTimeout != other.hardTimeout) return false
        if (start != other.start) return false
        if (end != other.end) return false
        if (status != other.status) return false
        if (failureReason != other.failureReason) return false
        if (configurerName != other.configurerName) return false
        if (aborterName != other.aborterName) return false
        if (scenarios != other.scenarios) return false
        if (zones != other.zones) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + creation.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + speedFactor.hashCode()
        result = 31 * result + (scheduledMinions ?: 0)
        result = 31 * result + (softTimeout?.hashCode() ?: 0)
        result = 31 * result + (hardTimeout?.hashCode() ?: 0)
        result = 31 * result + (start?.hashCode() ?: 0)
        result = 31 * result + (end?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + (configurerName?.hashCode() ?: 0)
        result = 31 * result + (aborterName?.hashCode() ?: 0)
        result = 31 * result + scenarios.hashCode()
        result = 31 * result + zones.hashCode()
        return result
    }

    override fun toString(): String {
        return "Campaign(version=$version, key='$key', creation=$creation, name='$name', speedFactor=$speedFactor, scheduledMinions=$scheduledMinions, softTimeout=$softTimeout, hardTimeout=$hardTimeout, start=$start, end=$end, result=$status, configurerName=$configurerName, aborterName=$aborterName, scenarios=$scenarios, zones=$zones)"
    }


}
