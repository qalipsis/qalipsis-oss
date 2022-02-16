package io.qalipsis.core.factory.orchestration

import com.google.common.collect.Table
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId

/**
 * Maintains a registry of the triple assignment of the minions to the DAGs and the factories.
 *
 * The factories are identified by the channel to use to distribute the data to each one.
 *
 * @author Eric Jessé
 */
internal interface MinionAssignmentKeeper {

    /**
     * Registers the [dagIds] assigned to the current factory when a new campaign starts.
     */
    suspend fun assignFactoryDags(
        campaignId: CampaignId,
        dagsByScenarios: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>
    )

    /**
     * Registers the minions identified by [minionIds] as executed on the DAGs identified by [dagIds].
     * This function is called only in the factory processing the [io.qalipsis.core.directives.MinionsAssignmentDirective].
     */
    suspend fun registerMinionsToAssign(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        dagIds: Collection<DirectedAcyclicGraphId>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean = true
    )

    /**
     * Notifies that the registration of unassigned minions is now complete for the scenario.
     * This function is called only in the factory processing the [io.qalipsis.core.directives.MinionsAssignmentDirective]
     * and aims at cleaning potential cache used during the registration process.
     */
    suspend fun completeUnassignedMinionsRegistration(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Returns all the IDs for the minions under load.
     */
    suspend fun getIdsOfMinionsUnderLoad(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ): Collection<MinionId>

    /**
     * Assign some still available minions for the DAGs passed as parameter to the factory reachable by the channel
     * [distributionChannelName].
     *
     * @return a set of assignment of minions to DAGs
     */
    suspend fun assign(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
    ): Map<MinionId, Collection<DirectedAcyclicGraphId>>

    /**
     * Marks the execution of a minion identified by [minionId] as complete for DAGs identified by [dagIds].
     *
     * @return a state of the completion of the minion, scenario and campaign.
     */
    suspend fun executionComplete(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphId>
    ): CampaignCompletionState

    /**
     * Returns the channels to use to forward data to the DAGs identified by [dagsIds] for the specified [minionIds].
     */
    suspend fun getFactoriesChannels(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphId>
    ): Table<MinionId, DirectedAcyclicGraphId, String>
}