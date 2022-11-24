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

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import java.time.Instant

internal interface FactoryCampaignManager : CampaignLifeCycleAware {

    val runningCampaign: Campaign

    /**
     * Verifies whether the campaign is locally executed.
     */
    fun isLocallyExecuted(campaignKey: CampaignKey): Boolean

    /**
     * Verifies whether the scenario is locally executed.
     */
    fun isLocallyExecuted(campaignKey: CampaignKey, scenarioName: ScenarioName): Boolean

    /**
     * Starts all the steps for a campaign and the related singleton minions.
     */
    suspend fun warmUpCampaignScenario(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Calculates the ramping of all the minions under load for the given scenario.
     */
    suspend fun prepareMinionsExecutionProfile(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        executionProfileConfiguration: ExecutionProfileConfiguration
    ): List<MinionsStartingLine>

    suspend fun notifyCompleteMinion(
        minionId: MinionId,
        minionStart: Instant,
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagId: DirectedAcyclicGraphName
    )

    /**
     * Shutdown the related minions of the specified campaign.
     */
    suspend fun shutdownMinions(campaignKey: CampaignKey, minionIds: Collection<MinionId>)

    /**
     * Stops all the components of a scenario in a campaign.
     */
    suspend fun shutdownScenario(campaignKey: CampaignKey, scenarioName: ScenarioName)

}