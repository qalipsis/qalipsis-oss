package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.ScenarioName
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Max
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
        required = true
    )
    @field:Positive
    @field:Max(999)
    val speedFactor: Double = 1.0,

    @field:Schema(
        description = "Time to wait before the first minion is executed, it should take the latency of the factories into consideration",
        required = true
    )
    @field:PositiveOrZero
    @field:Max(15000)
    val startOffsetMs: Long = 1000,

    @field:Schema(description = "Limit duration of the whole campaign before it is aborted", required = false)
    @field:PositiveOrZero
    val timeout: Duration? = null,

    @field:Schema(description = "Limit duration of the whole campaign before it is aborted", required = false)
    val hardTimeout: Boolean? = null,

    @field:Schema(description = "The map of the scenarios for the new campaign", required = true)
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
    @field:Schema(description = "Counts of minions that will be assigned to the scenario")
    @field:Positive
    @field:Max(1000000)
    val minionsCount: Int
)