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
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directives processed by the factories on behalf of the head.
 */

/**
 * Directive to prepare the creation of the minions for a given scenario. This directive can only be read once.
 * Hence, a [SingleUseDirective] with a single value is used, to remove the directive after the first read.
 *
 * It is published to the directive consumers as a [MinionsDeclarationDirectiveReference].
 */
@Serializable
@SerialName("md")
data class MinionsDeclarationDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val minionsCount: Int,
    override val channel: DispatcherChannel
) : SingleUseDirective<MinionsDeclarationDirectiveReference>(), CampaignManagementDirective {

    override var tenant: String = ""

    override fun toReference(key: DirectiveKey): MinionsDeclarationDirectiveReference {
        return MinionsDeclarationDirectiveReference(key, campaignKey, scenarioName)
    }
}

/**
 * Transportable representation of a [MinionsDeclarationDirective].
 */
@Serializable
@SerialName("mdRef")
data class MinionsDeclarationDirectiveReference(
    override val key: DirectiveKey,
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName
) : SingleUseDirectiveReference(), CampaignManagementDirective {

    override var tenant: String = ""
}

/**
 * Directive to calculate the ramp-up of the minions given the scenario strategy.
 *
 * Contrary to [MinionsDeclarationDirective], this directive is not single-use: every factory executing the scenario
 * receives it and prepares the ramp-up plan.
 *
 * What it brings: the head waits for one feedback from each factory and scenario. A factory that never prepared the
 * ramp-up is therefore visible, instead of blocking the campaign without any error.
 *
 * What it costs: all those factories calculate the very same plan, because the starting lines are derived from the
 * count of minions under load of the whole scenario, which is shared by the cluster.
 *
 * Why this redundancy is not an issue: the calculation only creates a few starting lines in memory, and the minions
 * are never scheduled twice. The deduplication happens in the registry: the first factory sets the flag
 * `minion:scheduling-executed` and writes the plan, the next ones are rejected by this flag - see
 * `schedule-minion.lua`. Each factory then reads only the minions that the plan assigned to itself.
 */
@Serializable
@SerialName("mrp")
data class MinionsRampUpPreparationDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val executionProfileConfiguration: ExecutionProfileConfiguration,
    override val channel: DispatcherChannel
) : DescriptiveDirective(), CampaignManagementDirective {

    override var tenant: String = ""
}
