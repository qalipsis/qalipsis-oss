package io.qalipsis.core.head.campaign

import io.qalipsis.api.context.ScenarioId

internal data class DataCampaignConfiguration(
    override val id: String,
    override val minionsCountPerScenario: Int = 0,
    override val minionsFactor: Double = 1.0,
    override val speedFactor: Double = 1.0,
    override val startOffsetMs: Long = 1000,
    override val scenarios: List<ScenarioId> = emptyList()
) : CampaignConfiguration {

    constructor(configuration: CampaignConfiguration) : this(
        id = configuration.id,
        minionsCountPerScenario = configuration.minionsCountPerScenario,
        minionsFactor = configuration.minionsFactor,
        speedFactor = configuration.speedFactor,
        startOffsetMs = configuration.startOffsetMs,
        scenarios = configuration.scenarios,
    )
}