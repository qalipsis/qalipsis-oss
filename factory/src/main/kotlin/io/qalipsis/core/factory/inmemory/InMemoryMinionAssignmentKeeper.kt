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

package io.qalipsis.core.factory.inmemory

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.orchestration.CampaignCompletionState
import io.qalipsis.core.factory.orchestration.LocalAssignmentStoreImpl
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of [MinionAssignmentKeeper] for standalone execution.
 * Since there is only one campaign running at a time, the cached data are not additionally keyed by
 * the campaign ID.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.STANDALONE])
internal class InMemoryMinionAssignmentKeeper(
    private val localAssignmentStore: LocalAssignmentStoreImpl
) : MinionAssignmentKeeper {

    /**
     * Number of DAGs to execute by scenario and minion.
     */
    private val dagsCountByMinions = concurrentTableOf<ScenarioName, MinionId, AtomicInteger>()

    /**
     * Collection of minions under load by scenario.
     */
    private val minionsByScenarios = ConcurrentHashMap<ScenarioName, MutableSet<MinionId>>()

    /**
     * Collection of the singleton minions.
     */
    private val singletonMinions = concurrentSet<MinionId>()

    private val executionCompleteMutex = Mutex(false)

    /**
     * The assignment of DAGs to a factory is not relevant when there is only one factory.
     */
    override suspend fun assignFactoryDags(
        campaignKey: CampaignKey,
        assignments: Collection<FactoryScenarioAssignment>
    ) {
        dagsCountByMinions.clear()
        minionsByScenarios.clear()
        localAssignmentStore.reset()
    }

    @LogInputAndOutput
    override suspend fun registerMinionsToAssign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean
    ) {
        // Creates the local assignments.
        val assignments = mutableMapOf<MinionId, MutableCollection<DirectedAcyclicGraphName>>()
        minionIds.forEach { minionId ->
            assignments.computeIfAbsent(minionId) { concurrentSet() } += dagIds
            if (underLoad) {
                dagsCountByMinions.computeIfAbsent(scenarioName, minionId) { AtomicInteger() }
                    .addAndGet(dagIds.size)
            }
        }
        localAssignmentStore.save(scenarioName, assignments)

        if (underLoad) {
            minionsByScenarios.computeIfAbsent(scenarioName) { concurrentSet() } += minionIds
        } else {
            this.singletonMinions += minionIds
        }
    }

    override suspend fun completeUnassignedMinionsRegistration(campaignKey: CampaignKey, scenarioName: ScenarioName) =
        Unit

    override suspend fun getIdsOfMinionsUnderLoad(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Collection<MinionId> {
        return minionsByScenarios[scenarioName]!! - singletonMinions
    }

    @LogInputAndOutput
    override suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>> {
        return localAssignmentStore.assignments[scenarioName] ?: emptyMap()
    }

    @LogInputAndOutput
    override suspend fun executionComplete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphName>
    ): CampaignCompletionState {
        val state = CampaignCompletionState()

        // Verifies the completion of the minion.
        if (singletonMinions.remove(minionId)) {
            state.minionComplete = true
        } else {
            val remainingDagsForMinion = dagsCountByMinions[scenarioName, minionId]?.addAndGet(-1 * dagIds.size)
            if (remainingDagsForMinion == 0) {
                log.trace { "The minion under load with ID $minionId of campaign $campaignKey executed all its steps and is now complete" }
                state.minionComplete = true
                executionCompleteMutex.withLock {
                    dagsCountByMinions.remove(scenarioName, minionId)
                    minionsByScenarios[scenarioName]!!.let { minionsInScenario ->
                        minionsInScenario.remove(minionId)

                        // The verification of the completeness of the scenario and campaign are synchronized to avoid collision.
                        if (minionsInScenario.isEmpty()) {
                            log.debug { "The scenario $scenarioName of campaign $campaignKey is now complete" }
                            state.scenarioComplete = true
                            minionsByScenarios.remove(scenarioName)
                            if (minionsByScenarios.isEmpty()) {
                                log.debug { "The campaign $campaignKey is now complete" }
                                state.campaignComplete = true
                                localAssignmentStore.reset()
                            }
                        }
                    }
                }
            } else {
                log.trace { "The minion with ID $minionId of campaign $campaignKey has still ${remainingDagsForMinion} DAGs to complete" }
            }
        }

        return state
    }

    override suspend fun getFactoriesChannels(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): Table<MinionId, DirectedAcyclicGraphName, String> {
        return EMPTY_TABLE
    }

    companion object {

        private val log = logger()

        private val EMPTY_TABLE = HashBasedTable.create<MinionId, DirectedAcyclicGraphName, String>()
    }
}