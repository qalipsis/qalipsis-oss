package io.qalipsis.core.head.model.converter

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Scenario
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
    private val scenarioRepository: CampaignScenarioRepository,
    private val idGenerator: IdGenerator
) : CampaignConverter {

    override suspend fun convertConfiguration(
        tenant: String,
        campaign: CampaignConfiguration
    ): RunningCampaign {
        return RunningCampaign(
            tenant = tenant,
            key = generateCampaignKey(tenant),
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            hardTimeout = campaign.hardTimeout ?: false,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    protected fun generateCampaignKey(tenant: String): String = idGenerator.long()

    override suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign {
        return Campaign(
            version = campaignEntity.version,
            key = campaignEntity.key,
            name = campaignEntity.name,
            speedFactor = campaignEntity.speedFactor,
            scheduledMinions = campaignEntity.scheduledMinions,
            timeout = campaignEntity.timeout,
            hardTimeout = campaignEntity.hardTimeout,
            start = campaignEntity.start,
            end = campaignEntity.end,
            result = campaignEntity.result,
            configurerName = userRepository.findUsernameById(campaignEntity.configurer),
            scenarios = scenarioRepository.findByCampaignId(campaignEntity.id).map { scenarioEntity ->
                Scenario(
                    scenarioEntity.version,
                    scenarioEntity.name,
                    scenarioEntity.minionsCount
                )
            }
        )
    }

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.mapValues { ScenarioConfiguration(it.value.minionsCount) }
    }
}