package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
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
import io.qalipsis.core.head.utils.SortingUtil
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.count
import java.time.Instant
import io.qalipsis.core.head.model.Page as QalipsisPage


@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
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
        }).count()

        return campaignConverter.convertToModel(campaign)
    }

    override suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign {
        campaignRepository.close(tenant, campaignKey, result)
        return campaignConverter.convertToModel(campaignRepository.findByKey(tenant, campaignKey))
    }

    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): QalipsisPage<Campaign> {
        // Default sorting for the campaign is done with the start time in reverse order, because it is always not null.
        val sorting = sort?.let { SortingUtil.sort(it) } ?: Sort.of(Sort.Order.desc(CampaignEntity::start.name))

        val pageable = Pageable.from(page, size, sorting)

        val entitiesPage = if (filters.isNotEmpty()) {
            val sanitizedFilters = filters.map { it.replace('*', '%') }.map { "%${it.trim()}%" }
            campaignRepository.findAll(tenant, sanitizedFilters, pageable)
        } else {
            campaignRepository.findAll(tenant, pageable)
        }

        return QalipsisPage(
            page = entitiesPage.pageNumber,
            totalPages = entitiesPage.totalPages,
            totalElements = entitiesPage.totalSize,
            elements = entitiesPage.content.map { campaignConverter.convertToModel(it) }
        )
    }

    override suspend fun setAborter(tenant: String, aborter: String, campaignKey: String) {
        val campaign = campaignRepository.findByKey(tenant, campaignKey)
        campaignRepository.update(campaign.copy(aborter = userRepository.findIdByUsername(aborter)))
    }

}