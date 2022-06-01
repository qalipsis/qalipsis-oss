package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.converter.CampaignConverter
import jakarta.inject.Singleton
import java.time.Instant

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.VOLATILE])
)
internal class PersistentCampaignService(
    private val campaignRepository: CampaignRepository,
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignConverter: CampaignConverter
) : CampaignService {

    override suspend fun create(
        configurer: String,
        campaignDisplayName: String,
        campaignConfiguration: CampaignConfiguration
    ): Campaign {
        val campaign = campaignRepository.save(
            CampaignEntity(
                tenantId = tenantRepository.findIdByReference(campaignConfiguration.tenant),
                key = campaignConfiguration.key,
                name = campaignDisplayName,
                speedFactor = campaignConfiguration.speedFactor,
                start = Instant.now(),
                configurer = userRepository.findIdByUsername(configurer)
            )
        )
        campaignScenarioRepository.saveAll(campaignConfiguration.scenarios.map { (scenarioName, scenario) ->
            CampaignScenarioEntity(campaign.id, scenarioName, scenario.minionsCount)
        })

        return campaignConverter.convertToModel(campaign)
    }

    override suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign {
        campaignRepository.close(tenant, campaignKey, result)
        return campaignConverter.convertToModel(campaignRepository.findByKey(tenant, campaignKey))
    }

}