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

import com.google.common.collect.Table
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.core.campaigns.FactoryScenarioAssignment

/**
 * Maintains a registry of the triple assignment of the minions to the DAGs and the factories.
 *
 * The factories are identified by the channel to use to distribute the data to each one.
 *
 * @author Eric Jessé
 */
internal interface MinionAssignmentKeeper {

    /**
     * Registers the Directed Acyclic Graphs assigned to the current factory when the currently prepared
     * campaign will start.
     */
    suspend fun assignFactoryDags(
        campaignKey: CampaignKey,
        assignments: Collection<FactoryScenarioAssignment>
    )

    /**
     * Registers the minions identified by [minionIds] as executed on the DAGs identified by [dagIds].
     * This function is called only in the factory processing the [io.qalipsis.core.directives.MinionsAssignmentDirective].
     */
    suspend fun registerMinionsToAssign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean = true
    )

    /**
     * Notifies that the registration of unassigned minions is now complete for the scenario.
     * This function is called only in the factory processing the [io.qalipsis.core.directives.MinionsAssignmentDirective]
     * and aims at cleaning potential cache used during the registration process.
     */
    suspend fun completeUnassignedMinionsRegistration(campaignKey: CampaignKey, scenarioName: ScenarioName)

    /**
     * Returns all the IDs for the minions under load.
     */
    suspend fun getIdsOfMinionsUnderLoad(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Collection<MinionId>

    /**
     * Returns the count of the minions under load for the scenario.
     */
    suspend fun countMinionsUnderLoad(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Int

    /**
     * Assign some still available minions for the DAGs passed as parameter to the factory reachable by the channel
     * [distributionChannelName].
     *
     * @return a set of assignment of minions to DAGs
     */
    suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>>

    /**
     * Schedules all the minions for the campaign and scenario, using the provided calculated starting lines.
     */
    suspend fun schedule(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        startingLines: Collection<MinionsStartingLine>
    )

    /**
     * Reads the scheduling for the current factory, and specified campaign and scenario.
     * The key specified the offset when the minions as values have to be started.
     */
    suspend fun readSchedulePlan(campaignKey: CampaignKey, scenarioName: ScenarioName): Map<Long, Collection<MinionId>>

    /**
     * Marks the execution of a minion identified by [minionId] as complete for DAGs identified by [dagIds].
     *
     * @param mightRestart indicates whether the minion is permitted to be restarted if complete
     *
     * @return a state of the completion of the minion, scenario and campaign.
     */
    suspend fun executionComplete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphName>,
        mightRestart: Boolean
    ): CampaignCompletionState

    /**
     * Returns the channels to use to forward data to the DAGs identified by [dagsIds] for the specified [minionIds].
     */
    suspend fun getFactoriesChannels(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): Table<MinionId, DirectedAcyclicGraphName, String>
}