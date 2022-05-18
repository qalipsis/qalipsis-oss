package io.qalipsis.core.head.web.entity

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.ScenarioName
import jakarta.inject.Singleton

/**
 * Convertor from [CampaignRequest] to [CampaignConfiguration].
 *
 * @author Palina Bril
 */
@Singleton
internal class CampaignConfigurationConverterImpl : CampaignConfigurationConverter {

    override fun convertCampaignRequestToConfiguration(
        tenant: String,
        campaign: CampaignRequest
    ): CampaignConfiguration {
        return CampaignConfiguration(
            tenant = tenant,
            name = campaign.name,
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.map { it.key to ScenarioConfiguration(it.value.minionsCount) }.toMap()
    }

}