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

package io.qalipsis.core.head.hook

import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.web.handler.BulkIllegalArgumentException
import jakarta.inject.Singleton

/**
 * Implementation of [CampaignHook] to validate the campaign to start, using the startup configuration constraints.
 *
 * @author Joël Valère
 */
@Singleton
internal class ValidationCampaignHook(
    private val campaignConstraints: DefaultCampaignConfiguration.Validation,
    private val headConfiguration: HeadConfiguration,
) : CampaignHook {

    override suspend fun preCreate(campaignConfiguration: CampaignConfiguration, runningCampaign: RunningCampaign) {
        val constraintsViolations = mutableListOf<String>()
        validateCampaign(campaignConfiguration, runningCampaign, constraintsViolations)
        runningCampaign.scenarios.values.forEach { validateZones(it.zones, constraintsViolations) }
        runningCampaign.scenarios.values.map { it.executionProfileConfiguration }
            .filterIsInstance<StageExecutionProfileConfiguration>()
            .forEach { validateStage(it, constraintsViolations) }

        if (constraintsViolations.isNotEmpty()) {
            throw BulkIllegalArgumentException(constraintsViolations)
        }
    }

    private fun validateCampaign(
        campaignConfiguration: CampaignConfiguration,
        runningCampaign: RunningCampaign,
        constraintsViolations: MutableCollection<String>
    ) {
        // The timeout validation is done separately.
        if (campaignConfiguration.scenarios.size > campaignConstraints.maxScenariosCount) {
            constraintsViolations += "The count of scenarios in the campaign should not exceed ${campaignConstraints.maxScenariosCount}"
        }

        if (runningCampaign.scenarios.values.sumOf { it.minionsCount } > campaignConstraints.maxMinionsCount) {
            constraintsViolations += "The count of minions in the campaign should not exceed ${campaignConstraints.maxMinionsCount}"
        }
    }

    private fun validateStage(
        configuration: StageExecutionProfileConfiguration,
        constraintsViolations: MutableCollection<String>
    ) {
        val stageConstraints = campaignConstraints.stage

        if (configuration.stages.any { it.resolutionMs < stageConstraints.minResolution.toMillis() }) {
            constraintsViolations += "The start resolution for a stage should at least be ${stageConstraints.minResolution.toMillis()} milliseconds"
        }
        if (configuration.stages.any { it.resolutionMs > stageConstraints.maxResolution.toMillis() }) {
            constraintsViolations += "The start resolution for a stage should be less than or equal to ${stageConstraints.maxResolution.toMillis()} milliseconds"
        }
        if (configuration.stages.any { it.minionsCount < stageConstraints.minMinionsCount }) {
            constraintsViolations += "The minimum minions count for a stage should be ${stageConstraints.minMinionsCount}"
        }
        if (stageConstraints.maxMinionsCount?.let { maxMinionsCount -> configuration.stages.any { it.minionsCount > maxMinionsCount } } == true) {
            constraintsViolations += "The maximum minions count for a stage should be ${stageConstraints.maxMinionsCount}"
        }
        if (configuration.stages.any { it.totalDurationMs < stageConstraints.minDuration.toMillis() }) {
            constraintsViolations += "The minimum duration for a stage should be ${stageConstraints.minDuration.toMillis()} milliseconds"
        }
        if (stageConstraints.maxDuration?.let { maxDuration -> configuration.stages.any { it.totalDurationMs > maxDuration.toMillis() } } == true) {
            constraintsViolations += "The maximum duration for a stage should be ${stageConstraints.maxDuration!!.toMillis()} milliseconds"
        }
        if (stageConstraints.minStartDuration?.let { minStartDuration -> configuration.stages.any { it.rampUpDurationMs < minStartDuration.toMillis() } } == true) {
            constraintsViolations += "The minimum ramp-up duration for a stage should be ${stageConstraints.minStartDuration!!.toMillis()} milliseconds"
        }
        if (stageConstraints.maxStartDuration?.let { maxStartDuration -> configuration.stages.any { it.rampUpDurationMs > maxStartDuration.toMillis() } } == true) {
            constraintsViolations += "The maximum ramp-up duration for a stage should be ${stageConstraints.maxStartDuration!!.toMillis()} milliseconds"
        }
    }

    private fun validateZones(zones: Map<String, Int>, constraintsViolations: MutableCollection<String>) {

        /**
         * Keys of the zones supported by the cluster.
         */
        val allowedZones = headConfiguration.cluster.zones.map { it.key }.toSet()

        if (zones.isNotEmpty()) {
            if (!allowedZones.containsAll(zones.keys)) {
                val unknownZones = zones.keys - allowedZones
                constraintsViolations += "The requested zones ${unknownZones.joinToString()} are not known"
            }

            if (zones.entries.sumOf { it.value } != 100) {
                constraintsViolations += "The distribution of the load across the different zones should equal to 100%"
            }
        }

    }
}