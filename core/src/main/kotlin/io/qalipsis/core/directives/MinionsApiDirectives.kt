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
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directive to trigger the assignment and creation of the minions for a scenario
 * in a new test campaign.
 */
@Serializable
@SerialName("ma")
data class MinionsAssignmentDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Definition of an instant when a given minion has to start.
 */
@Serializable
data class MinionStartDefinition(val minionId: MinionId, val timestamp: Long)

/**
 * Directive to start a set of minions at a certain point in time for a given scenario.
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("ms")
data class MinionsStartDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val startDefinitions: List<MinionStartDefinition>
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""

    override fun toString(): String {
        return "MinionsStartDirective(campaignKey='$campaignKey', scenarioName='$scenarioName', startDefinitionsCount=${startDefinitions.size})"
    }
}

/**
 * Directive to shutdown all the components of a minion.
 */
@Serializable
@SerialName("msd")
data class MinionsShutdownDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val minionIds: List<MinionId>,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}
