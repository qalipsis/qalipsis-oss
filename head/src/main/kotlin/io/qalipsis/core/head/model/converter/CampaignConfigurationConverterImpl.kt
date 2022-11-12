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
import io.qalipsis.core.head.configuration.HeadConfiguration
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
    private val idGenerator: IdGenerator,
    private val headConfiguration: HeadConfiguration
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
            hardTimeout = campaign.hardTimeout ?: false,
            scenarios = convertScenarioRequestsToConfigurations(campaign.scenarios)
        )
    }

    protected fun generateCampaignKey(tenant: String): String = idGenerator.long()

    private fun convertScenarioRequestsToConfigurations(scenarios: Map<ScenarioName, ScenarioRequest>): Map<ScenarioName, ScenarioConfiguration> {
        return scenarios.mapValues {
            ScenarioConfiguration(
                minionsCount = it.value.minionsCount,
                executionProfileConfiguration = defineExecutionProfileConfiguration(it.value),
                zones = it.value.zones?.also(this::checkAndUpdateZone).orEmpty()
            )
        }
    }

    private fun checkAndUpdateZone(zones: Map<String, Int>){
        //We check if all the keys provide for zones are supported
        require(headConfiguration.zones.map { it.key }.containsAll(zones.keys)){
            val unknownZones = zones.keys.filterNot { headConfiguration.zones.map { zone -> zone.key }.contains(it) }
            "Some requested zones do not exist: ${unknownZones.joinToString()}"
        }

        require(zones.isEmpty() || zones.entries.sumOf { it.value } == 100){
            "The distribution of the load across the different zones should equal to 100%"
        }
    }

    private fun defineExecutionProfileConfiguration(scenario: ScenarioRequest): ExecutionProfileConfiguration {
        return when (val config = scenario.executionProfile) {
            is RegularExternalExecutionProfileConfiguration -> RegularExecutionProfileConfiguration(
                config.periodInMs,
                config.minionsCountProLaunch
            )

            is AcceleratingExternalExecutionProfileConfiguration -> AcceleratingExecutionProfileConfiguration(
                config.startPeriodMs,
                config.accelerator,
                config.minPeriodMs,
                config.minionsCountProLaunch
            )

            is ProgressiveVolumeExternalExecutionProfileConfiguration -> ProgressiveVolumeExecutionProfileConfiguration(
                config.periodMs,
                config.minionsCountProLaunchAtStart,
                config.multiplier,
                config.maxMinionsCountProLaunch
            )

            is StageExternalExecutionProfileConfiguration -> StageExecutionProfileConfiguration(
                config.completion,
                config.stages.map {
                    Stage(
                        minionsCount = it.minionsCount,
                        rampUpDurationMs = it.rampUpDurationMs,
                        totalDurationMs = it.totalDurationMs,
                        resolutionMs = it.resolutionMs
                    )
                }
            )

            is TimeFrameExternalExecutionProfileConfiguration -> TimeFrameExecutionProfileConfiguration(
                config.periodInMs,
                config.timeFrameInMs
            )

            else -> DefaultExecutionProfileConfiguration()
        }
    }
}