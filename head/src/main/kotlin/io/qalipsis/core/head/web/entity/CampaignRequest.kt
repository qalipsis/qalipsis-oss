package io.qalipsis.core.head.web.entity

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.ScenarioName
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

@Introspected
internal data class CampaignRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 300)
    val name: CampaignName,
    @field:Positive
    @field:Max(999)
    val speedFactor: Double = 1.0,
    val startOffsetMs: Long = 1000,
    @field:NotEmpty
    val scenarios: Map<ScenarioName, ScenarioConfiguration>,
    @field:NotBlank
    val username: String
)
