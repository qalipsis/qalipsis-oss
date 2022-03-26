package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.collections.Table
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class LocalAssignmentStoreImpl(
    private val scenarioRegistry: ScenarioRegistry
) : LocalAssignmentStore {

    /**
     * Table to track the assignments of the minions in the local factory.
     */
    private val actualAssignments = concurrentTableOf<ScenarioId, MinionId, MutableCollection<DirectedAcyclicGraphId>>()

    /**
     * Map of root DAGs under load keyed by their scenarios ID.
     */
    private lateinit var rootDagsUnderLoad: Map<ScenarioId, DirectedAcyclicGraphId>

    override val assignments: Table<ScenarioId, MinionId, out Collection<DirectedAcyclicGraphId>>
        get() = actualAssignments

    override fun hasMinionsAssigned(scenarioId: ScenarioId): Boolean {
        return actualAssignments[scenarioId]?.isNotEmpty() == true
    }

    override fun save(scenarioId: ScenarioId, assignments: Map<MinionId, Collection<DirectedAcyclicGraphId>>) {
        assignments.forEach { (minionId, dagsIds) ->
            actualAssignments.computeIfAbsent(scenarioId, minionId) { concurrentSet() } += dagsIds
        }
    }

    override fun isLocal(scenarioId: ScenarioId, minionId: MinionId, dagId: DirectedAcyclicGraphId): Boolean {
        return actualAssignments[scenarioId, minionId]?.contains(dagId) ?: false
    }

    override fun hasRootUnderLoadLocally(scenarioId: ScenarioId, minionId: MinionId): Boolean {
        return rootDagsUnderLoad[scenarioId]?.let { dagId ->
            isLocal(scenarioId, minionId, dagId)
        } ?: false
    }

    override fun reset() {
        actualAssignments.clear()
        rootDagsUnderLoad = scenarioRegistry.all()
            .associate { scenario -> scenario.id to scenario.dags.first { it.isRoot && it.isUnderLoad }.id }
    }
}