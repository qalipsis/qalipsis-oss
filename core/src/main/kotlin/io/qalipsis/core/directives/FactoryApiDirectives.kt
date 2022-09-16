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

package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.configuration.AbortRunningCampaign
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.validation.constraints.NotEmpty

/**
 * Directive notifying a unique factory of the DAGs assigned to it in the context of a new campaign.
 *
 * @property campaignKey the ID of the campaign
 */
@Serializable
@SerialName("fa")
data class FactoryAssignmentDirective(
    override val campaignKey: CampaignKey,
    @field:NotEmpty
    val assignments: Collection<FactoryScenarioAssignment>,
    val broadcastChannel: DispatcherChannel,
    val feedbackChannel: DispatcherChannel,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to warm-up the component of a scenario (steps...) when a new campaign starts.
 */
@Serializable
@SerialName("wup")
data class ScenarioWarmUpDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to shutdown all the components of a scenario in a campaign.
 */
@Serializable
@SerialName("ssd")
data class CampaignScenarioShutdownDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to shutdown all the components of a campaign.
 */
@Serializable
@SerialName("csd")
data class CampaignShutdownDirective(
    override val campaignKey: CampaignKey,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to notify components from the completion of a campaign.
 */
@Serializable
@SerialName("ccd")
data class CompleteCampaignDirective(
    val campaignKey: CampaignKey,
    val isSuccessful: Boolean = true,
    val message: String? = null,
    override val channel: DispatcherChannel
) : DescriptiveDirective()


/**
 * Directive to shutdown a factory.
 */
@Serializable
@SerialName("fsd")
data class FactoryShutdownDirective(
    override val channel: DispatcherChannel
) : DescriptiveDirective()


/**
 * Directive to abort the campaign for a given list of scenarios.
 */
@Serializable
@SerialName("cad")
data class CampaignAbortDirective(
    override val campaignKey: CampaignKey,
    override val channel: DispatcherChannel,
    /**
     * The list of scenario IDs for which the campaign has to be aborted.
     */
    val scenarioNames: List<ScenarioName>,
    /**
     * Configuration defining soft/hard aborting mode.
     */
    val abortRunningCampaign: AbortRunningCampaign = AbortRunningCampaign(hard = true)
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""

}
