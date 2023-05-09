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
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.core.head.utils.SortingUtil
import io.qalipsis.core.head.utils.SqlFilterUtils.formatsFilters
import jakarta.inject.Singleton
import java.time.Instant
import io.qalipsis.api.query.Page as QalipsisPage


@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.TRANSIENT])
)
internal class PersistentCampaignService(
    private val campaignRepository: CampaignRepository,
    private val userRepository: UserRepository,
    private val campaignScenarioRepository: CampaignScenarioRepository,
    private val campaignConverter: CampaignConverter,
    private val factoryRepository: FactoryRepository,
    private val campaignPreparator: CampaignPreparator
) : CampaignService {

    @LogInputAndOutput
    override suspend fun create(
        tenant: String,
        configurer: String,
        campaignConfiguration: CampaignConfiguration
    ): RunningCampaign = campaignPreparator.convertAndSaveCampaign(tenant, configurer, campaignConfiguration)

    @LogInputAndOutput
    override suspend fun retrieve(tenant: String, campaignKey: CampaignKey): Campaign {
        val campaign = requireNotNull(
            campaignRepository.findByTenantAndKey(
                tenant,
                campaignKey
            )
        ) { "The requested campaign could not be found" }
        return campaignConverter.convertToModel(campaign)
    }

    override suspend fun retrieveConfiguration(tenant: String, campaignKey: CampaignKey): CampaignConfiguration {
        val campaign = requireNotNull(
            campaignRepository.findByTenantAndKey(
                tenant,
                campaignKey
            )
        ) { "The requested campaign could not be found" }
        return requireNotNull(campaign.configuration) { "No configuration could be found for the expected campaign" }
    }

    @LogInputAndOutput
    override suspend fun prepare(tenant: String, campaignKey: CampaignKey) {
        campaignRepository.prepare(tenant, campaignKey)
    }

    @LogInput
    override suspend fun start(
        tenant: String,
        campaignKey: CampaignKey,
        start: Instant,
        softTimeout: Instant?,
        hardTimeout: Instant?
    ) {
        campaignRepository.start(tenant, campaignKey, start, softTimeout, hardTimeout)
    }

    @LogInput
    override suspend fun startScenario(
        tenant: String,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        start: Instant
    ) {
        campaignScenarioRepository.start(tenant, campaignKey, scenarioName, start)
    }

    @LogInput
    override suspend fun closeScenario(tenant: String, campaignKey: CampaignKey, scenarioName: ScenarioName) {
        campaignScenarioRepository.complete(tenant, campaignKey, scenarioName)
    }

    @LogInput
    override suspend fun close(
        tenant: String,
        campaignKey: CampaignKey,
        result: ExecutionStatus,
        failureReason: String?
    ): Campaign {
        campaignRepository.complete(tenant, campaignKey, result, failureReason)
        return retrieve(tenant, campaignKey)
    }

    @LogInput
    override suspend fun search(
        tenant: String, filters: Collection<String>, sort: String?, page: Int, size: Int
    ): QalipsisPage<Campaign> {
        // Default sorting for the campaign is done with the start time in reverse order, because it is always not null.
        val sorting = sort?.let { SortingUtil.sort(CampaignEntity::class, it) }
            ?: Sort.of(Sort.Order.desc(CampaignEntity::start.name))

        val pageable = Pageable.from(page, size, sorting)

        val entitiesPage = if (filters.isNotEmpty()) {
            campaignRepository.findAll(tenant, filters.formatsFilters(), pageable)
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

    @LogInput
    override suspend fun abort(tenant: String, aborter: String?, campaignKey: String) {
        campaignRepository.findByTenantAndKey(tenant, campaignKey)?.let { campaign ->
            campaignRepository.update(campaign.copy(aborter = aborter?.let { userRepository.findIdByUsername(it) }))
        }
    }

    override suspend fun enrich(runningCampaign: RunningCampaign) {
        campaignRepository.findByTenantAndKey(runningCampaign.tenant, runningCampaign.key)?.let { campaign ->
            val factories = factoryRepository.findByNodeIdIn(runningCampaign.tenant, runningCampaign.factories.keys)
            campaignRepository.update(
                campaign.copy(
                    zones = factories.mapNotNull { it.zone }.toSet(),
                    failureReason = runningCampaign.message.takeIf(String::isNotBlank)
                )
            )
        }
    }
}