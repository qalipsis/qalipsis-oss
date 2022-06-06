package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.runtime.Minion
import java.time.Instant

/**
 *
 * @author Eric Jessé
 */
interface MinionsKeeper {

    /**
     * Returns the minion with the expected ID.
     */
    operator fun get(minionId: MinionId): Minion

    /**
     * Returns the singleton minion attached to a singleton DAG or DAG not under load.
     */
    fun getSingletonMinion(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): Minion

    /**
     * Verifies if the minion with the expected ID exists.
     */
    operator fun contains(minionId: MinionId): Boolean

    /**
     * Creates a new Minion for the given scenario and directed acyclic graph.
     *
     * @param campaignKey the ID of the campaign to execute.
     * @param scenarioName the ID of the scenario to execute.
     * @param dagIds the IDs of the directed acyclic graphs the minion will execute.
     * @param minionId the ID of the minion.
     */
    suspend fun create(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionId: MinionId
    )

    /**
     * Starts all the singletons minions of the given scenario.
     *
     * @param scenarioName the ID of the scenario in execution to which the minions belong.
     */
    suspend fun startSingletons(scenarioName: ScenarioName)

    /**
     * Starts a minion at the specified instant.
     *
     * @param minionId the ID of the minion to start.
     * @param instant the instant when the minion has to start. If the instant is already in the past, the minion starts immediately.
     */
    suspend fun scheduleMinionStart(minionId: MinionId, instant: Instant)

    /**
     * Immediately starts a minion.
     *
     * @param minionId the ID of the minion to start.
     */
    suspend fun restartMinion(minionId: MinionId)

    /**
     * Stops and removes the specified minion.
     *
     * @param minionId the ID of the minion to shutdown and remove.
     */
    suspend fun shutdownMinion(minionId: MinionId)

    /**
     * Stops and removes all the minions.
     */
    suspend fun shutdownAll()
}
