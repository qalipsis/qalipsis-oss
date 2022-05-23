package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.beans.BeanIntrospection
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
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
    private val campaignScenarioRepository: CampaignScenarioRepository
) : CampaignService {

    override suspend fun create(configurer: String, campaignConfiguration: CampaignConfiguration) {
        val campaign = campaignRepository.save(
            CampaignEntity(
                campaignName = campaignConfiguration.name,
                speedFactor = campaignConfiguration.speedFactor,
                start = Instant.now(),
                configurer = userRepository.findIdByUsername(configurer)
            )
        )
        campaignScenarioRepository.saveAll(campaignConfiguration.scenarios.map { (scenarioName, scenario) ->
            CampaignScenarioEntity(campaign.id, scenarioName, scenario.minionsCount)
        })
    }

    override suspend fun close(campaignName: CampaignName, result: ExecutionStatus) {
        campaignRepository.close(campaignName, result)
    }

    override suspend fun getAllCampaigns(tenant: String, filter: String?, sort: String?): List<CampaignEntity> {
        val filters = filter?.let { filter.split(",").map { it.trim() } }
        sort?.let {
            val sortProperty = BeanIntrospection.getIntrospection(CampaignEntity::class.java).beanProperties
                .firstOrNull {
                    it.name == sort.trim().split(":").get(0)
                }
            val sortOrder = sort.trim().split(":").last()
            return if ("desc" == sortOrder) {
                if (filters?.isNotEmpty() == true) {
                    campaignRepository.findAll(tenant, filters).sortedBy { sortProperty?.get(it) as Comparable<Any> }
                        .reversed()
                } else {
                    campaignRepository.findAll(tenant).sortedBy { sortProperty?.get(it) as Comparable<Any> }
                        .reversed()
                }
            } else {
                if (filters?.isNotEmpty() == true) {
                    campaignRepository.findAll(tenant, filters).sortedBy { sortProperty?.get(it) as Comparable<Any> }
                } else {
                    campaignRepository.findAll(tenant).sortedBy { sortProperty?.get(it) as Comparable<Any> }
                }
            }
        }
        return if (filters?.isNotEmpty() == true) {
            campaignRepository.findAll(tenant, filters)
        } else {
            campaignRepository.findAll(tenant)
        }
    }
}