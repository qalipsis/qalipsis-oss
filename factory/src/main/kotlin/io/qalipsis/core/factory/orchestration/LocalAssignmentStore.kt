package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.collections.Table

/**
 * Registry in charge of maintaining the assignment of the minions in the current factory for the campaign in progress.
 *
 * Since only one campaign can run in a factory at a time.
 *
 * @author Eric Jessé
 */
interface LocalAssignmentStore {

    /**
     * Table of the minions assigned to the local factory for the current campaign, keyed by scenario and attached
     * to the DAGs, which the minion locally executes.
     */
    val assignments: Table<ScenarioId, MinionId, out Collection<DirectedAcyclicGraphId>>

    /**
     * Verifies whether minions are applied to the current factory for the scenario [scenarioId].
     */
    fun hasMinionsAssigned(scenarioId: ScenarioId): Boolean

    /**
     * Registers new minions assigned to the local factory for the current campaign.
     *
     * @param scenarioId the scenario for which the minions run
     * @param assignments all the minions that are locally assigned and the DAGs they locally execute
     */
    fun save(scenarioId: ScenarioId, assignments: Map<MinionId, Collection<DirectedAcyclicGraphId>>)

    /**
     * Verifies whether a minion is executing a DAG in the local factory.
     *
     * @param scenarioId the scenario for which the minions run
     * @param minionId the minion to verify
     * @param dagId the DAG, for which the local execution of [minionId] is to verify
     *
     * @return [true] when the minion with ID [minionId] executes the DAG [dagId] in the current factory, [false] otherwise
     */
    fun isLocal(scenarioId: ScenarioId, minionId: MinionId, dagId: DirectedAcyclicGraphId): Boolean

    /**
     * Verifies whether a minion under load has its root DAG in the current factory.
     *
     * @param scenarioId the scenario for which the minions run
     * @param minionId the minion to verify
     *
     * @return [true] when the minion with ID [minionId] executes the root DAG under load in the current factory, [false] otherwise
     */
    fun hasRootUnderLoadLocally(scenarioId: ScenarioId, minionId: MinionId): Boolean

    /**
     * Clears all the local assignments.
     */
    fun reset()
}