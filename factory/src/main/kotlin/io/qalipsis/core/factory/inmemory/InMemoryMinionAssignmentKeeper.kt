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
import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.executionprofile.MinionsStartingLine
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
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
import org.slf4j.event.Level
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
@Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.SINGLE_FACTORY])
class InMemoryMinionAssignmentKeeper(
    private val localAssignmentStore: LocalAssignmentStoreImpl
) : MinionAssignmentKeeper {

    /**
     * Originally assigned count of DAGs to execute by scenario and minion.
     */
    @KTestable
    private val scheduledDagsCountByMinions = concurrentTableOf<ScenarioName, MinionId, AtomicInteger>()

    private val scheduledStartOffsets = concurrentTableOf<ScenarioName, Long, Collection<MinionId>>()

    /**
     * Currently assigned count of DAGs to execute by scenario and minion.
     */
    @KTestable
    private val remainingDagsCountByMinions = concurrentTableOf<ScenarioName, MinionId, AtomicInteger>()

    /**
     * Collection of minions under load by scenario.
     */
    private val minionsByScenarios = ConcurrentHashMap<ScenarioName, MutableSet<MinionId>>()

    /**
     * Collection of the singleton minions.
     */
    private val singletonMinions = concurrentSet<MinionId>()

    private val executionCompleteMutex = Mutex(false)

    init {
        log.debug { "Using the InMemoryMinionAssignmentKeeper to assign the minions" }
    }

    /**
     * The assignment of DAGs to a factory is not relevant when there is only one factory.
     */
    @LogInputAndOutput(Level.DEBUG)
    override suspend fun assignFactoryDags(
        campaignKey: CampaignKey,
        assignments: Collection<FactoryScenarioAssignment>
    ) {
        scheduledDagsCountByMinions.clear()
        remainingDagsCountByMinions.clear()
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
                remainingDagsCountByMinions.computeIfAbsent(scenarioName, minionId) { AtomicInteger() }
                    .addAndGet(dagIds.size)
                scheduledDagsCountByMinions.computeIfAbsent(scenarioName, minionId) { AtomicInteger() }
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

    @LogInputAndOutput(Level.DEBUG)
    override suspend fun countMinionsUnderLoad(campaignKey: CampaignKey, scenarioName: ScenarioName): Int {
        return getIdsOfMinionsUnderLoad(campaignKey, scenarioName).size
    }

    @LogInputAndOutput(Level.DEBUG)
    override suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>> {
        return localAssignmentStore.assignments[scenarioName] ?: emptyMap()
    }

    @LogInput
    override suspend fun schedule(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        startingLines: Collection<MinionsStartingLine>
    ) {
        val minionsUnderLoad = getIdsOfMinionsUnderLoad(campaignKey, scenarioName).toMutableList()
        val scenarioSchedule = mutableMapOf<Long, MutableCollection<MinionId>>()
        startingLines.forEach { startingLine ->
            repeat(startingLine.count) {
                if (minionsUnderLoad.isNotEmpty()) {
                    scenarioSchedule.computeIfAbsent(startingLine.offsetMs) { mutableSetOf() } += minionsUnderLoad.removeAt(
                        0
                    )
                }
            }
        }

        assert(minionsUnderLoad.isEmpty()) { "${minionsUnderLoad.size} minions could not be scheduled" }
        scheduledStartOffsets[scenarioName] = scenarioSchedule
    }

    @LogInput(Level.DEBUG)
    override suspend fun readSchedulePlan(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Map<Long, Collection<MinionId>> {
        return scheduledStartOffsets[scenarioName]!!
    }

    @LogInputAndOutput
    override suspend fun executionComplete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphName>,
        mightRestart: Boolean
    ): CampaignCompletionState {
        val state = CampaignCompletionState()

        // Verifies the completion of the minion.
        if (singletonMinions.remove(minionId)) {
            state.minionComplete = true
        } else {
            val remainingDagsForMinion =
                remainingDagsCountByMinions[scenarioName, minionId]?.addAndGet(-1 * dagIds.size)
            if (remainingDagsForMinion == 0) {
                log.trace { "The minion under load with ID $minionId of campaign $campaignKey executed all its steps and is now complete" }
                state.minionComplete = true
                if (mightRestart) {
                    remainingDagsCountByMinions.put(
                        scenarioName,
                        minionId,
                        AtomicInteger(scheduledDagsCountByMinions[scenarioName, minionId]!!.get())
                    )
                } else {
                    cleanMinionDataAndEvaluateScenarioCompletion(scenarioName, minionId, campaignKey, state)
                }
            } else {
                log.trace { "The minion with ID $minionId of campaign $campaignKey has still $remainingDagsForMinion DAGs to complete" }
            }
        }

        return state
    }

    private suspend fun cleanMinionDataAndEvaluateScenarioCompletion(
        scenarioName: ScenarioName,
        minionId: MinionId,
        campaignKey: CampaignKey,
        state: CampaignCompletionState
    ) {
        scheduledDagsCountByMinions.remove(scenarioName, minionId)
        remainingDagsCountByMinions.remove(scenarioName, minionId)
        executionCompleteMutex.withLock {
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