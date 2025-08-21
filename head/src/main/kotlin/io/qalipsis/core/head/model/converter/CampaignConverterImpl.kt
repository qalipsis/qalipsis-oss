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

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.security.UserProvider
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignConverter].
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
@Requires(missingBeans = [CampaignConverter::class])
class CampaignConverterImpl(
    private val userProvider: UserProvider,
    private val scenarioRepository: CampaignScenarioRepository
) : CampaignConverter {

    override suspend fun convertToModel(campaignEntity: CampaignEntity): Campaign {
        val configurerName = userProvider.findUsernameById(campaignEntity.configurer)
        val aborterName = campaignEntity.aborter?.let {
            if (campaignEntity.configurer == campaignEntity.aborter) {
                configurerName
            } else {
                userProvider.findUsernameById(campaignEntity.aborter)
            }
        }
        return Campaign(
            version = campaignEntity.version,
            creation = campaignEntity.creation,
            key = campaignEntity.key,
            name = campaignEntity.name,
            speedFactor = campaignEntity.speedFactor,
            scheduledMinions = campaignEntity.scheduledMinions,
            softTimeout = campaignEntity.softTimeout,
            hardTimeout = campaignEntity.hardTimeout,
            start = campaignEntity.start,
            end = campaignEntity.end,
            status = campaignEntity.result ?: when (campaignEntity.start) {
                null -> ExecutionStatus.QUEUED
                else -> ExecutionStatus.IN_PROGRESS
            },
            failureReason = campaignEntity.failureReason,
            zones = campaignEntity.zones,
            configurerName = userProvider.findUsernameById(campaignEntity.configurer),
            aborterName = aborterName,
            scenarios = scenarioRepository.findByCampaignId(campaignEntity.id).map { scenarioEntity ->
                Scenario(
                    scenarioEntity.version,
                    scenarioEntity.name,
                    scenarioEntity.minionsCount
                )
            }
        )
    }
}