package io.qalipsis.core.head.model.converter

import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.ScenarioRequest
import jakarta.inject.Singleton

/**
 * Convertor for different formats around the campaigns.
 *
 * @author Palina Bril
 */
@Singleton
internal class CampaignConverterImpl(
    private val userRepository: UserRepository,
    private val idGenerator: IdGenerator
) : CampaignConverter {

    override suspend fun convertRequest(
        tenant: String,
        campaign: CampaignRequest
    ): CampaignConfiguration {
        return CampaignConfiguration(
            tenant = tenant,
            key = idGenerator.short(),
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.map { it.key to ScenarioConfiguration(it.value.minionsCount) }.toMap()
    }

    override suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign {
        return Campaign(
            version = campaignEntity.version,
            key = campaignEntity.key,
            name = campaignEntity.name,
            speedFactor = campaignEntity.speedFactor,
            start = campaignEntity.start,
            end = campaignEntity.end,
            result = campaignEntity.result,
            configurerName = userRepository.findUsernameById(campaignEntity.configurer)
        )
    }

}