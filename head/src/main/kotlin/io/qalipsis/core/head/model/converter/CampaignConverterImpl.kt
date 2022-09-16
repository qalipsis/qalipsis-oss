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