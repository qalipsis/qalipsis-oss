package io.qalipsis.core.factory.orchestration

import com.google.common.collect.Table
import io.qalipsis.api.campaign.FactoryScenarioAssignment
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName

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
        campaignName: CampaignName,
        assignments: Collection<FactoryScenarioAssignment>
    )

    /**
     * Registers the minions identified by [minionIds] as executed on the DAGs identified by [dagIds].
     * This function is called only in the factory processing the [io.qalipsis.core.directives.MinionsAssignmentDirective].
     */
    suspend fun registerMinionsToAssign(
        campaignName: CampaignName,
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
    suspend fun completeUnassignedMinionsRegistration(campaignName: CampaignName, scenarioName: ScenarioName)

    /**
     * Returns all the IDs for the minions under load.
     */
    suspend fun getIdsOfMinionsUnderLoad(
        campaignName: CampaignName,
        scenarioName: ScenarioName
    ): Collection<MinionId>

    /**
     * Assign some still available minions for the DAGs passed as parameter to the factory reachable by the channel
     * [distributionChannelName].
     *
     * @return a set of assignment of minions to DAGs
     */
    suspend fun assign(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>>

    /**
     * Marks the execution of a minion identified by [minionId] as complete for DAGs identified by [dagIds].
     *
     * @return a state of the completion of the minion, scenario and campaign.
     */
    suspend fun executionComplete(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphName>
    ): CampaignCompletionState

    /**
     * Returns the channels to use to forward data to the DAGs identified by [dagsIds] for the specified [minionIds].
     */
    suspend fun getFactoriesChannels(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): Table<MinionId, DirectedAcyclicGraphName, String>
}