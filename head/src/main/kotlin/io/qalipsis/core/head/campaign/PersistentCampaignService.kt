package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import jakarta.inject.Singleton
import java.time.Instant

@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.VOLATILE])
)
internal class PersistentCampaignService(
    private val campaignRepository: CampaignRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository
) : CampaignService {

    override suspend fun save(campaignConfiguration: CampaignConfiguration) {
        val campaign = campaignRepository.save(
            CampaignEntity(
                campaignId = campaignConfiguration.id,
                speedFactor = campaignConfiguration.speedFactor,
                start = Instant.now()
            )
        )
        campaignScenarioRepository.saveAll(campaignConfiguration.scenarios.map { (scenarioId, scenario) ->
            CampaignScenarioEntity(campaign.id, scenarioId, scenario.minionsCount)
        })
    }

    override suspend fun close(campaignId: CampaignId, result: ExecutionStatus) {
        campaignRepository.close(campaignId, result)
    }

}