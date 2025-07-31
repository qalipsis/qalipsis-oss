/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import io.micronaut.context.annotation.Secondary
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.qalipsis.core.head.model.DefaultValuesCampaignConfiguration
import io.qalipsis.core.head.model.Stage
import io.qalipsis.core.head.model.Validation
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignConfigurationProvider] that provides default values and configuration rules
 * for new campaigns, based upon the static configuration of QALIPSIS.
 */
@Singleton
@Secondary
class DefaultCampaignConfigurationProvider(
    private val defaultCampaignConfiguration: DefaultCampaignConfiguration
) : CampaignConfigurationProvider {

    override suspend fun retrieveCampaignConfigurationDetails(tenant: String): DefaultValuesCampaignConfiguration {
        return DefaultValuesCampaignConfiguration(
            validation = Validation(
                maxMinionsCount = defaultCampaignConfiguration.validation.maxMinionsCount,
                maxExecutionDuration = defaultCampaignConfiguration.validation.maxExecutionDuration,
                maxScenariosCount = defaultCampaignConfiguration.validation.maxScenariosCount,
                stage = Stage(
                    minMinionsCount = defaultCampaignConfiguration.validation.stage.minMinionsCount,
                    maxMinionsCount = defaultCampaignConfiguration.validation.stage.maxMinionsCount
                        ?: defaultCampaignConfiguration.validation.maxMinionsCount,
                    minResolution = defaultCampaignConfiguration.validation.stage.minResolution,
                    maxResolution = defaultCampaignConfiguration.validation.stage.maxResolution,
                    minDuration = defaultCampaignConfiguration.validation.stage.minDuration,
                    maxDuration = defaultCampaignConfiguration.validation.stage.maxDuration
                        ?: defaultCampaignConfiguration.validation.maxExecutionDuration,
                    minStartDuration = defaultCampaignConfiguration.validation.stage.minStartDuration,
                    maxStartDuration = defaultCampaignConfiguration.validation.stage.maxStartDuration
                )
            )
        )
    }
}