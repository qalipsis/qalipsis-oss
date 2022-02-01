package io.qalipsis.core.factory.inmemory

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
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
    private val dagsCountByMinions = concurrentTableOf<ScenarioId, MinionId, AtomicInteger>()

    /**
     * Collection of minions under load by scenario.
     */
    private val minionsByScenarios = ConcurrentHashMap<ScenarioId, MutableSet<MinionId>>()

    /**
     * Collection of the singleton minions.
     */
    private val singletonMinions = concurrentSet<MinionId>()

    private val executionCompleteMutex = Mutex(false)

    /**
     * The assignment of DAGs to a factory is not relevant when there is only one factory.
     */
    override suspend fun assignFactoryDags(
        campaignId: CampaignId,
        dagsByScenarios: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>
    ) {
        dagsCountByMinions.clear()
        minionsByScenarios.clear()
        localAssignmentStore.reset()
    }

    @LogInputAndOutput
    override suspend fun registerMinionsToAssign(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        dagIds: Collection<DirectedAcyclicGraphId>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean
    ) {
        // Creates the local assignments.
        val assignments = mutableMapOf<MinionId, MutableCollection<DirectedAcyclicGraphId>>()
        minionIds.forEach { minionId ->
            assignments.computeIfAbsent(minionId) { concurrentSet() } += dagIds
            if (underLoad) {
                dagsCountByMinions.computeIfAbsent(scenarioId, minionId) { AtomicInteger() }
                    .addAndGet(dagIds.size)
            }
        }
        localAssignmentStore.save(scenarioId, assignments)

        if (underLoad) {
            minionsByScenarios.computeIfAbsent(scenarioId) { concurrentSet() } += minionIds
        } else {
            this.singletonMinions += minionIds
        }
    }

    override suspend fun completeUnassignedMinionsRegistration(campaignId: CampaignId, scenarioId: ScenarioId) = Unit

    override suspend fun getIdsOfMinionsUnderLoad(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ): Collection<MinionId> {
        return minionsByScenarios[scenarioId]!! - singletonMinions
    }

    @LogInputAndOutput
    override suspend fun assign(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ): Map<MinionId, Collection<DirectedAcyclicGraphId>> {
        return localAssignmentStore.assignments[scenarioId] ?: emptyMap()
    }

    @LogInputAndOutput
    override suspend fun executionComplete(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphId>
    ): CampaignCompletionState {
        val state = CampaignCompletionState()

        // Verifies the completion of the minion.
        if (singletonMinions.remove(minionId)) {
            state.minionComplete = true
        } else {
            executionCompleteMutex.withLock {
                val remainingDagsForMinion = dagsCountByMinions[scenarioId, minionId]?.addAndGet(-1 * dagIds.size)
                if (remainingDagsForMinion == 0) {
                    state.minionComplete = true
                    dagsCountByMinions.remove(scenarioId, minionId)
                    minionsByScenarios[scenarioId]!!.let { minionsInScenario ->
                        minionsInScenario.remove(minionId)

                        // The verification of the completeness of the scenario and campaign are synchronized to avoid collision.
                        if (minionsInScenario.isEmpty()) {
                            state.scenarioComplete = true
                            minionsByScenarios.remove(scenarioId)
                            if (minionsByScenarios.isEmpty()) {
                                state.campaignComplete = true
                                localAssignmentStore.reset()
                            }
                        }
                    }
                }
            }
        }

        return state
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}