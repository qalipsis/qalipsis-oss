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
