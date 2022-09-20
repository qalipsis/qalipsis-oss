/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.utils.SortingUtil
import jakarta.inject.Singleton
import java.time.Instant
import kotlinx.coroutines.flow.count
import io.qalipsis.api.query.Page as QalipsisPage


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
    private val campaignConfigurationConverter: CampaignConfigurationConverter,
    private val campaignConverter: CampaignConverter
) : CampaignService {

    override suspend fun create(
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign {
        val runningCampaign = campaignConfigurationConverter.convertConfiguration(tenant, campaignConfiguration)
        val campaign = campaignRepository.save(
            CampaignEntity(
                tenantId = tenantRepository.findIdByReference(tenant),
                key = runningCampaign.key,
                name = campaignConfiguration.name,
                scheduledMinions = campaignConfiguration.scenarios.values.sumOf { it.minionsCount },
                hardTimeout = campaignConfiguration.hardTimeout ?: false,
                speedFactor = campaignConfiguration.speedFactor,
                configurer = requireNotNull(userRepository.findIdByUsername(configurer))
            )
        )
        campaignScenarioRepository.saveAll(campaignConfiguration.scenarios.map { (scenarioName, scenario) ->
            CampaignScenarioEntity(campaignId = campaign.id, name = scenarioName, minionsCount = scenario.minionsCount)
        }).count()

        return runningCampaign
    }

    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign {
        val campaign = requireNotNull(campaignRepository.findByTenantAndKey(tenant, campaignKey))
        return campaignConverter.convertToModel(campaign)
    }

    override suspend fun start(tenant: String, campaignKey: CampaignKey, start: Instant, timeout: Instant?) {
        campaignRepository.start(tenant, campaignKey, start, timeout)
    }

    override suspend fun startScenario(
        tenant: String,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        start: Instant
    ) {
        campaignScenarioRepository.start(tenant, campaignKey, scenarioName, start)
    }

    override suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName) {
        campaignScenarioRepository.complete(tenant, campaignKey, scenarioName)
    }

    override suspend fun close(tenant: String, campaignKey: String, result: ExecutionStatus): Campaign {
        campaignRepository.complete(tenant, campaignKey, result)
        return retrieve(tenant, campaignKey)
    }

    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): QalipsisPage<Campaign> {
        // Default sorting for the campaign is done with the start time in reverse order, because it is always not null.
        val sorting = sort?.let { SortingUtil.sort(CampaignEntity::class, it) } ?: Sort.of(
            Sort.Order.desc(
                CampaignEntity::start.name
            )
        )

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

    override suspend fun abort(tenant: String, aborter: String, campaignKey: String) {
        campaignRepository.findByTenantAndKey(tenant, campaignKey)?.let { campaign ->
            campaignRepository.update(campaign.copy(aborter = userRepository.findIdByUsername(aborter)))
        }
    }

}