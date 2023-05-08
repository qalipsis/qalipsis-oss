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
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.configuration.AcceleratingExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.ProgressiveVolumeExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.RegularExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.StageExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.TimeFrameExternalExecutionProfileConfiguration
import jakarta.inject.Singleton

/**
 * Converter for different formats around the campaigns.
 *
 * @author Palina Bril
 */
@Singleton
internal class CampaignConfigurationConverterImpl(
    private val idGenerator: IdGenerator
) : CampaignConfigurationConverter {

    override suspend fun convertConfiguration(
        tenant: String,
        campaign: CampaignConfiguration
    ): RunningCampaign {
        return RunningCampaign(
            tenant = tenant,
            key = generateCampaignKey(tenant),
            speedFactor = campaign.speedFactor,
            startOffsetMs = campaign.startOffsetMs,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    protected fun generateCampaignKey(tenant: String): String = idGenerator.long()

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.mapValues {
            val (config, minionsCount) = defineExecutionProfileConfiguration(it.value)
            ScenarioConfiguration(
                minionsCount = minionsCount,
                executionProfileConfiguration = config,
                zones = it.value.zones.orEmpty()
            )
        }
    }

    /**
     * Converts the requested execution profile to the internal format and the total minions count for the scenario.
     */
    private fun defineExecutionProfileConfiguration(
        scenario: ScenarioRequest
    ): Pair<ExecutionProfileConfiguration, Int> {
        return when (val config = scenario.executionProfile) {
            is RegularExternalExecutionProfileConfiguration -> RegularExecutionProfileConfiguration(
                config.periodInMs,
                config.minionsCountProLaunch
            ) to scenario.minionsCount

            is AcceleratingExternalExecutionProfileConfiguration -> AcceleratingExecutionProfileConfiguration(
                config.startPeriodMs,
                config.accelerator,
                config.minPeriodMs,
                config.minionsCountProLaunch
            ) to scenario.minionsCount

            is ProgressiveVolumeExternalExecutionProfileConfiguration -> ProgressiveVolumeExecutionProfileConfiguration(
                config.periodMs,
                config.minionsCountProLaunchAtStart,
                config.multiplier,
                config.maxMinionsCountProLaunch
            ) to scenario.minionsCount

            is StageExternalExecutionProfileConfiguration -> {
                val totalMinionsCount = config.stages.sumOf { it.minionsCount }
                StageExecutionProfileConfiguration(
                    config.completion,
                    config.stages.map {
                        Stage(
                            minionsCount = it.minionsCount,
                            rampUpDurationMs = it.rampUpDurationMs,
                            totalDurationMs = it.totalDurationMs,
                            resolutionMs = it.resolutionMs
                        )
                    }
                ) to totalMinionsCount
            }

            is TimeFrameExternalExecutionProfileConfiguration -> TimeFrameExecutionProfileConfiguration(
                config.periodInMs,
                config.timeFrameInMs
            ) to scenario.minionsCount

            else -> DefaultExecutionProfileConfiguration() to scenario.minionsCount
        }
    }
}