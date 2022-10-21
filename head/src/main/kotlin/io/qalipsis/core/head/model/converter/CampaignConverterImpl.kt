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

import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Scenario
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignConverter].
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
internal class CampaignConverterImpl(
    private val userRepository: UserRepository,
    private val scenarioRepository: CampaignScenarioRepository
) : CampaignConverter {

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
            },
            configuration = campaignEntity.configuration
        )
    }
}