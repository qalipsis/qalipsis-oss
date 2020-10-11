package io.qalipsis.api.orchestration.factories

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import java.time.Instant

/**
 *
 * @author Eric Jessé
 */
interface MinionsKeeper {

    /**
     * Returns the minion with the expected ID.
     */
    operator fun get(minionId: MinionId): Collection<Minion>

    /**
     * Returns the singleton minion attached to a singleton DAG or DAG not under load.
     */
    fun getSingletonMinion(dagId: DirectedAcyclicGraphId): Minion

    /**
     * Verifies if the minion with the expected ID exists.
     */
    fun has(minionId: MinionId): Boolean

    /**
     * Creates a new Minion for the given scenario and directed acyclic graph.
     *
     * @param campaignId the ID of the campaign to execute.
     * @param scenarioId the ID of the scenario to execute.
     * @param dagId the ID of the directed acyclic graph to execute in the scenario.
     * @param minionId the ID of the minion.
     */
    fun create(campaignId: CampaignId, scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId, minionId: MinionId)

    /**
     * Starts all the steps for a campaign and the related singleton minions.
     */
    suspend fun startCampaign(campaignId: CampaignId, scenarioId: ScenarioId)

    /**
     * Starts a minion at the specified instant.
     *
     * @param minionId the ID of the minion to start.
     * @param instant the instant when the minion has to start. If the instant is already in the past, the minion starts immediately.
     */
    suspend fun startMinionAt(minionId: MinionId, instant: Instant)
}
