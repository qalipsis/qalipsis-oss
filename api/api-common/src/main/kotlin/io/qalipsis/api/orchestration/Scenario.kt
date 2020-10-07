package io.qalipsis.api.orchestration

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.Step
import io.qalipsis.api.sync.Slot
import io.qalipsis.core.factories.orchestration.rampup.RampUpStrategy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [Scenario] represents a full chain of [DirectedAcyclicGraph]s containing all the steps to perform.
 */
class Scenario(
        /**
         * ID of the Scenario.
         */
        val id: ScenarioId,

        /**
         * List of all the [DirectedAcyclicGraph]s of the scenario.
         */
        val dags: MutableList<DirectedAcyclicGraph> = CopyOnWriteArrayList(),

        /**
         * [RampUpStrategy] to define how fast the minions should be started when executing the scenario.
         */
        val rampUpStrategy: RampUpStrategy,

        /**
         * [RetryPolicy] to define on the steps of the scenario when none is explicitly set.
         */
        val defaultRetryPolicy: RetryPolicy = NoRetryPolicy(),

        /**
         * Default minions count to run the scenario when runtime factor is 1.
         */
        val minionsCount: Int = 1
) {

    private val steps = ConcurrentHashMap<StepId, Slot<Pair<Step<*, *>, DirectedAcyclicGraph>>>()

    /**
     * Adds a step to the scenario.
     */
    suspend fun addStep(dag: DirectedAcyclicGraph, step: Step<*, *>) {
        steps.computeIfAbsent(step.id) { Slot() }.set(step to dag)
    }

    /**
     * Finds a step with the expected ID or suspend until it is created or a timeout of 10 seconds happens.
     */
    suspend fun findStep(stepId: StepId): Pair<Step<*, *>, DirectedAcyclicGraph>? {
        return steps.computeIfAbsent(stepId) { Slot() }.get()
    }

}
