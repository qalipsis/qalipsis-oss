package io.qalipsis.core.head.web.entity

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.ScenarioName
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero
import javax.validation.constraints.Size

@Introspected
internal data class CampaignRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 300)
    val name: CampaignName,

    @field:Positive
    @field:Max(999)
    val speedFactor: Double = 1.0,

    @field:PositiveOrZero
    @field:Max(15000)
    val startOffsetMs: Long = 1000,

    @field:Valid
    @field:NotEmpty
    val scenarios: Map<@NotBlank ScenarioName, @Valid ScenarioRequest>
)

@Introspected
internal data class ScenarioRequest(
    @field:Positive
    @field:Max(1000000)
    val minionsCount: Int
)