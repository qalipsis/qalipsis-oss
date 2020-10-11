package io.qalipsis.api.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factories.orchestration.rampup.RampUpStrategy

/**
 * A [Scenario] represents a full chain of [DirectedAcyclicGraph]s containing all the steps to perform.
 *
 * @property id ID of the scenario
 * @property rampUpStrategy defines how fast the minions should be started when executing the scenario
 * @property defaultRetryPolicy defines on the steps of the scenario when none is explicitly set
 * @property minionsCount default minions count to run the scenario when runtime factor is 1
 * @property dags collection of the [DirectedAcyclicGraph] of the scenario
 */
interface Scenario {
    val id: ScenarioId
    val rampUpStrategy: RampUpStrategy
    val defaultRetryPolicy: RetryPolicy
    val minionsCount: Int
    val dags: Collection<DirectedAcyclicGraph>

    /**
     * Verifies of the scenario contains the DAG with the specified ID.
     */
    operator fun contains(dagId: DirectedAcyclicGraphId): Boolean

    /**
     * Returns the DAG with the specified ID if it exists.
     */
    operator fun get(dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph?

    /**
     * Returns the DAG with the specified ID or create it if it does not yet exist.
     */
    fun createIfAbsent(dagId: DirectedAcyclicGraphId,
                       dagSupplier: (DirectedAcyclicGraphId) -> DirectedAcyclicGraph): DirectedAcyclicGraph

    /**
     * Adds a step to the scenario.
     */
    suspend fun addStep(dag: DirectedAcyclicGraph, step: Step<*, *>)

    /**
     * Finds a step with the expected ID or suspend until it is created or a timeout of 10 seconds happens.
     */
    suspend fun findStep(stepId: StepId): Pair<Step<*, *>, DirectedAcyclicGraph>?

    /**
     * Starts a new campaign on the scenario.
     */
    fun start(campaignId: CampaignId)

    /**
     * Stops a running campaign on the scenario.
     */
    fun stop(campaignId: CampaignId)

    /**
     * Destroys the scenario and all its components recursively.
     */
    fun destroy()
}