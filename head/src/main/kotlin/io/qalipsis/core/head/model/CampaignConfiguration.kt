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
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.head.model.configuration.ExternalExecutionProfileConfiguration
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size

/**
 * Model to create a new campaign configuration.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Describe the details of a campaign to execute",
    title = "Details for the campaign configuration to start a new campaign into QALIPSIS"
)
internal data class CampaignConfiguration(
    @field:Schema(description = "Name of the campaign", required = true)
    @field:NotBlank
    @field:Size(min = 3, max = 300)
    val name: String,

    @field:Schema(
        description = "Speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation",
        required = true,
        example = "1.0"
    )
    @field:Positive
    @field:Max(999)
    val speedFactor: Double = 1.0,

    @field:Schema(
        description = "Time to wait before the first minion is executed, it should take the latency of the factories into consideration",
        required = true,
        example = "1000"
    )
    @field:PositiveOrZero
    @field:Max(15000)
    val startOffsetMs: Long = 1000,

    @field:Schema(
        description = "Limit duration of the whole campaign before it is aborted",
        required = false,
        example = "PT2M"
    )
    @field:PositiveOrZero
    val timeout: Duration? = null,

    @field:Schema(description = "Limit duration of the whole campaign before it is aborted", required = false)
    val hardTimeout: Boolean? = null,

    @field:Schema(
        description = "The map of the scenarios for the new campaign", required = true,
        example = """{"my-first-scenario": {"minionsCount": 100, "executionProfile": {"type": "STAGE", "stages": [{"minionsCount": 100, "rampUpDurationMs": 10000, "totalDurationMs": 60000, "completion": "GRACEFUL"}]}}, "zones": {"FR": 40, "US": 30, "CN": 30}}""",
    )
    @field:Valid
    @field:NotEmpty
    val scenarios: Map<@NotBlank ScenarioName, @Valid ScenarioRequest>
)

/**
 * Model to create a new scenario configuration.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "ScenarioConfiguration request",
    title = "Details for the scenario configuration to start a new campaign into QALIPSIS"
)
internal data class ScenarioRequest(
    @field:Schema(description = "Counts of minions that will be assigned to the scenario", example = "100")
    @field:Positive
    @field:Max(1000000)
    val minionsCount: Int,

    @field:Schema(
        description = "The configuration of the execution profile to execute a scenario",
        example = """{"type": "STAGE", "stages": [{"minionsCount": 100, "rampUpDurationMs": 10000, "totalDurationMs": 60000, "completion": "GRACEFUL"}]}}"""
    )
    @field:Valid
    @field:NotEmpty
    val executionProfile: ExternalExecutionProfileConfiguration? = null,

    @field:Schema(description = "Distribution of the execution by zone", example = """{"FR":40, "US": 30, "CN": 30}""")
    @field:Valid
    val zones: Map<@NotBlank String, @Min(1) @Max(100) Int>? = null
)