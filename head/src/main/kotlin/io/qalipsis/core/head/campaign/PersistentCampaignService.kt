package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.utils.SortingUtil
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

    override suspend fun getAllCampaigns(
        tenant: String, filter: String?, sort: String?, page: Int, size: Int
    ): Page<CampaignEntity> {
        val filters = filter?.let { filter.split(",").map { "%${it.trim()}%" } }?.toMutableList()
        var pageable = Pageable.from(page, size)
        sort?.let {
            val sorting = SortingUtil.sort(sort)
            sorting?.let { pageable = Pageable.from(page, size, sorting) }
        }
        val campaignSet = mutableSetOf<CampaignEntity>()
        return if (filters?.isNotEmpty() == true) {
            filters.forEach {
                campaignSet += campaignRepository.findAll(tenant, it)
            }
            createPageFromList(campaignSet.toList(), pageable)
        } else {
            campaignRepository.findAll(tenant, pageable)
        }
    }

    private fun createPageFromList(list: List<CampaignEntity>, pageable: Pageable): Page<CampaignEntity> {
        val startOfPage: Int = pageable.number * pageable.size
        if (startOfPage > list.size) {
            return Page.of(emptyList(), pageable, 0)
        }
        val endOfPage = Math.min(startOfPage + pageable.size, list.size)
        return Page.of(list.subList(startOfPage, endOfPage), pageable, list.size.toLong())
    }
}